package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.client.PrivacyMode;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.client.upload.UploadManager;
import com.micaftic.morpher.core.network.NetworkAccess;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded loader and cache for the immutable public card thumbnails.
 *
 * <p>Only a revision whose catalog row advertises {@code thumbnail_status=ready}
 * on the anonymous public route is fetched; such a card therefore never
 * downloads model bytes. Every other row keeps the legacy live preview, so an
 * older server behaves exactly as before and a missing image silently falls
 * back to the placeholder icon.</p>
 *
 * <p>Textures are built off the render thread from verified PNG bytes and are
 * uploaded lazily by the existing GUI texture path. Eviction and release run on
 * the render thread.</p>
 */
public final class CloudThumbnailService {
    private static final long RETRY_MILLIS = 30_000L;

    private static final Object LOCK = new Object();
    private static final Map<CloudAssetRef, OuterFileTexture> READY = new LinkedHashMap<>(32, 0.75f, true);
    private static final Set<CloudAssetRef> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Map<CloudAssetRef, Long> FAILED_UNTIL = new ConcurrentHashMap<>();
    private static final AtomicInteger ACTIVE = new AtomicInteger();

    private CloudThumbnailService() {}

    /** True when this card should show a static image and needs no model bytes. */
    public static boolean expectsThumbnail(CloudAssetSummary summary) {
        if (!CloudThumbnailPolicy.usesThumbnail(summary)) return false;
        Long failedUntil = FAILED_UNTIL.get(summary.ref());
        return failedUntil == null || failedUntil <= System.currentTimeMillis();
    }

    /** Cards that still need the legacy live preview. */
    public static List<CloudAssetSummary> withoutReadyThumbnails(List<CloudAssetSummary> entries) {
        return CloudThumbnailPolicy.legacyPreviews(entries);
    }

    /** Resident static image for one exact revision; render thread only. */
    public static OuterFileTexture texture(CloudAssetRef ref) {
        if (ref == null) return null;
        synchronized (LOCK) {
            OuterFileTexture texture = READY.get(ref);
            if (texture != null) pruneLocked();
            return texture;
        }
    }

    /** Requests the visible page's thumbnails without blocking the frame. */
    public static void request(CloudClientRuntime.RuntimeState runtime, List<CloudAssetSummary> entries) {
        if (runtime == null || entries == null || entries.isEmpty()) return;
        if (!NetworkAccess.allowsCloud() || PrivacyMode.isActive()) return;
        for (CloudAssetSummary entry : entries) {
            if (!expectsThumbnail(entry)) continue;
            CloudAssetRef ref = entry.ref();
            synchronized (LOCK) { if (READY.containsKey(ref)) continue; }
            if (!IN_FLIGHT.add(ref)) continue;
            if (ACTIVE.get() >= CloudThumbnailPolicy.MAX_CONCURRENT_FETCHES) {
                IN_FLIGHT.remove(ref);
                return;
            }
            ACTIVE.incrementAndGet();
            fetch(runtime, entry);
        }
    }

    private static void fetch(CloudClientRuntime.RuntimeState runtime, CloudAssetSummary entry) {
        CloudAssetRef ref = entry.ref();
        CloudDiskCache disk = runtime.diskCache();
        String key = CloudDiskCache.thumbnailKey(entry.thumbnail());
        if (disk != null) {
            byte[] stored = disk.readThumbnail(key);
            if (stored != null) {
                OuterFileTexture cached = byteArrayTexture(stored);
                if (cached != null) {
                    ACTIVE.decrementAndGet();
                    IN_FLIGHT.remove(ref);
                    FAILED_UNTIL.remove(ref);
                    synchronized (LOCK) { READY.put(ref, cached); }
                    return;
                }
                disk.remove(key);
            }
        }
        var request = runtime.http().getPublicThumbnail(entry.thumbnail(), CloudThumbnailPolicy.MAX_BYTES);
        request.whenComplete((response, failure) -> {
            ACTIVE.decrementAndGet();
            IN_FLIGHT.remove(ref);
            byte[] bytes = response == null || failure != null || response.statusCode() != 200
                    ? null : response.body();
            OuterFileTexture texture = byteArrayTexture(bytes);
            if (texture == null) {
                FAILED_UNTIL.put(ref, System.currentTimeMillis() + RETRY_MILLIS);
                return;
            }
            FAILED_UNTIL.remove(ref);
            synchronized (LOCK) { READY.put(ref, texture); }
            if (disk != null) {
                // A card image is immutable, so a stored copy is always the exact same pixels.
                com.micaftic.morpher.util.SmExecutors.submit(
                        com.micaftic.morpher.util.SmExecutors.Pool.MODEL_IO, () -> disk.writeThumbnail(key, bytes));
            }
        });
    }

    /** A missing, anonymous or malformed image is a silent placeholder, never an error. */
    private static OuterFileTexture byteArrayTexture(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > CloudThumbnailPolicy.MAX_BYTES) return null;
        if (!CloudThumbnailPolicy.isThumbnailPng(bytes)) return null;
        try {
            return new OuterFileTexture(bytes);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    /** Drops every resident image; safe from any thread. */
    public static void clear() {
        List<OuterFileTexture> closing;
        synchronized (LOCK) {
            closing = new ArrayList<>(READY.values());
            READY.clear();
        }
        IN_FLIGHT.clear();
        FAILED_UNTIL.clear();
        if (closing.isEmpty()) return;
        Runnable release = () -> closing.forEach(CloudThumbnailService::release);
        // Only the render-thread check is portable across the supported versions.
        if (com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
            release.run();
        } else {
            net.minecraft.client.Minecraft.getInstance().execute(release);
        }
    }

    public static int residentCount() {
        synchronized (LOCK) { return READY.size(); }
    }

    private static void pruneLocked() {
        int excess = READY.size() - CloudThumbnailPolicy.MAX_TEXTURES;
        if (excess <= 0) return;
        List<OuterFileTexture> evicted = new ArrayList<>(excess);
        var iterator = READY.entrySet().iterator();
        while (excess-- > 0 && iterator.hasNext()) {
            evicted.add(iterator.next().getValue());
            iterator.remove();
        }
        for (OuterFileTexture texture : evicted) release(texture);
    }

    private static void release(OuterFileTexture texture) {
        try {
            UploadManager.removeTexture(texture);
            texture.closeAndReleaseSource();
        } catch (RuntimeException ignored) {
            // A texture released during shutdown must never break the caller.
        }
    }
}
