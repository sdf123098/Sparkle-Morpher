package com.micaftic.morpher.cloud.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure decisions about the static card image, kept free of client/GL classes
 * so the rules stay unit-testable outside the game.
 *
 * <p>A card switches from the live preview to a thumbnail only when the catalog
 * row says the image is ready on the anonymous public route and the public CDN
 * route is enabled. Everything else keeps the previous behaviour.</p>
 */
public final class CloudThumbnailPolicy {
    /** One card image is at most this large; the Cloud target is 20-40 KB. */
    public static final long MAX_BYTES = 512L * 1024L;
    public static final int MAX_DIMENSION = 1024;
    /** Resident GPU images. */
    public static final int MAX_TEXTURES = 64;
    public static final int MAX_CONCURRENT_FETCHES = 4;

    private CloudThumbnailPolicy() {}

    /** True when the card image comes from the thumbnail route instead of model bytes. */
    public static boolean usesThumbnail(CloudAssetSummary summary) {
        return usesThumbnail(summary, CloudCdnPolicy.enabled());
    }

    /** Same decision with the public route switch passed in, so it stays testable. */
    public static boolean usesThumbnail(CloudAssetSummary summary, boolean publicRouteEnabled) {
        return summary != null && publicRouteEnabled && summary.thumbnailReady() && summary.isPublic();
    }

    /**
     * The subset that still needs the legacy live preview. Cards served by a
     * thumbnail are dropped so the list downloads no model bytes for them.
     */
    public static List<CloudAssetSummary> legacyPreviews(List<CloudAssetSummary> entries) {
        if (entries == null || entries.isEmpty()) return entries == null ? List.of() : entries;
        List<CloudAssetSummary> legacy = new ArrayList<>(entries.size());
        for (CloudAssetSummary entry : entries) if (!usesThumbnail(entry)) legacy.add(entry);
        return legacy;
    }

    /** Keeps only the immutable anonymous thumbnail route. */
    public static boolean isPublicThumbnailPath(String path) {
        if (path == null) return false;
        if (path.isEmpty() || path.length() > 512) return false;
        if (!path.startsWith("/v1/public/assets/") || !path.endsWith("/thumbnail")) return false;
        if (path.contains("://") || path.contains("..") || path.indexOf('\\') >= 0) return false;
        for (int index = 0; index < path.length(); index++) {
            char current = path.charAt(index);
            if (current < 0x21 || current == 0x7f) return false;
        }
        return true;
    }

    /** Accepts only a single PNG frame inside the client image budget. */
    public static boolean isThumbnailPng(byte[] bytes) {
        if (bytes == null || bytes.length < 24) return false;
        if ((bytes[0] & 0xFF) != 0x89 || bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G') return false;
        if (bytes[12] != 'I' || bytes[13] != 'H' || bytes[14] != 'D' || bytes[15] != 'R') return false;
        int width = readInt(bytes, 16);
        int height = readInt(bytes, 20);
        return width >= 1 && height >= 1 && width <= MAX_DIMENSION && height <= MAX_DIMENSION;
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }
}
