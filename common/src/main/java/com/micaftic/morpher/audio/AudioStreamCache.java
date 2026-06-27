package com.micaftic.morpher.audio;

import com.micaftic.morpher.ResourceCleanupHelper;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.util.ResourceLifecycleStats;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import java.util.concurrent.Executor;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class AudioStreamCache {

    private static final IdentityHashMap<ModelAssembly, WeakReference<CachedAudioStreamProvider>> providerCache = new IdentityHashMap<>();

    private static final Object LOCK = new Object();

    public static IAudioStreamProvider getOrCreateProvider(ModelAssembly renderContext) {
        CachedAudioStreamProvider existingProvider;
        RenderSystem.assertOnRenderThread();
        WeakReference<CachedAudioStreamProvider> weakReference = providerCache.get(renderContext);
        if (weakReference != null && (existingProvider = weakReference.get()) != null) {
            return existingProvider;
        }
        CachedAudioStreamProvider newProvider = new CachedAudioStreamProvider();
        ResourceCleanupHelper.registerCleanup(newProvider, renderContext, it -> {
            ((Executor) Minecraft.getInstance()).execute(() -> {
                providerCache.remove(it);
            });
        });
        providerCache.put(renderContext, new WeakReference<>(newProvider));
        return newProvider;
    }

    public static void clearForModel(ModelAssembly renderContext) {
        if (renderContext == null) {
            return;
        }
        synchronized (LOCK) {
            WeakReference<CachedAudioStreamProvider> weakReference = providerCache.remove(renderContext);
            CachedAudioStreamProvider provider = weakReference == null ? null : weakReference.get();
            if (provider != null) {
                provider.clear("model evicted");
            }
        }
    }

    public static void clearAll(String reason) {
        synchronized (LOCK) {
            for (WeakReference<CachedAudioStreamProvider> weakReference : providerCache.values()) {
                CachedAudioStreamProvider provider = weakReference == null ? null : weakReference.get();
                if (provider != null) {
                    provider.clear(reason);
                }
            }
            providerCache.clear();
        }
    }

    public static class CachedAudioStreamProvider implements IAudioStreamProvider {

        private final ConcurrentHashMap<AudioTrackData, CachedAudioEntry> cachedEntries = new ConcurrentHashMap<>();

        private final ConcurrentHashMap<AudioTrackData, Object> pendingTracks = new ConcurrentHashMap<>();

        private final AtomicLong cachedBytes = new AtomicLong();

        CachedAudioStreamProvider() {
        }

        public void cacheAudioData(AudioTrackData trackData, ByteBuffer byteBuffer, IntArrayList intArrayList) {
            int byteSize = byteBuffer == null ? 0 : byteBuffer.remaining();
            if (byteSize <= 0 || maxCacheBytes() <= 0 || byteSize > maxCacheBytes()) {
                this.pendingTracks.remove(trackData);
                return;
            }
            CachedAudioEntry previous = this.cachedEntries.put(trackData, new CachedAudioEntry(byteBuffer, new AudioFormat(trackData.getSampleRate(), 16, 1, true, false), intArrayList, byteSize, System.currentTimeMillis()));
            if (previous != null) {
                this.cachedBytes.addAndGet(-previous.byteSize);
            }
            this.cachedBytes.addAndGet(byteSize);
            ResourceLifecycleStats.onAudioTrackCached(null, byteSize);
            trimToBudget();
            this.pendingTracks.remove(trackData);
        }

        void cancelAudioData(AudioTrackData trackData) {
            this.pendingTracks.remove(trackData);
        }

        @Override
        public IAudioStreamSupport createAudioStream(AudioTrackData trackData) throws UnsupportedAudioFileException, IOException {
            AudioCacheBuilder cacheBuilder;
            CachedAudioEntry audioEntry = this.cachedEntries.get(trackData);
            if (audioEntry != null) {
                audioEntry.lastUsedAt = System.currentTimeMillis();
                return new SeekableAudioStream(audioEntry.audioData.duplicate(), audioEntry.seekPositions, audioEntry.audioFormat);
            }
            if (trackData.getDuration() / trackData.getSampleRate() <= 4 && !this.pendingTracks.contains(trackData)) {
                cacheBuilder = new AudioCacheBuilder(this, trackData);
                this.pendingTracks.put(trackData, AudioStreamCache.LOCK);
            } else {
                cacheBuilder = null;
            }
            try {
                return switch (trackData.getCodec()) {
                    case VORBIS -> new OggVorbisAudioStream(trackData.getData(), cacheBuilder);
                    case OPUS -> new OggOpusAudioStream(trackData.getData(), cacheBuilder);
                    default -> throw new UnsupportedAudioFileException();
                };
            } catch (UnsupportedAudioFileException | IOException | RuntimeException e) {
                if (cacheBuilder != null) {
                    cacheBuilder.discard();
                }
                throw e;
            }
        }

        public void clear(String reason) {
            long released = 0L;
            for (CachedAudioEntry entry : this.cachedEntries.values()) {
                released += entry.byteSize;
            }
            this.cachedEntries.clear();
            this.pendingTracks.clear();
            this.cachedBytes.set(0L);
            if (released > 0L) {
                ResourceLifecycleStats.onAudioTrackReleased(null, released);
            }
        }

        private void trimToBudget() {
            long budget = maxCacheBytes();
            while (budget > 0 && this.cachedBytes.get() > budget) {
                AudioTrackData oldestKey = null;
                CachedAudioEntry oldestEntry = null;
                for (var entry : this.cachedEntries.entrySet()) {
                    if (oldestEntry == null || entry.getValue().lastUsedAt < oldestEntry.lastUsedAt) {
                        oldestKey = entry.getKey();
                        oldestEntry = entry.getValue();
                    }
                }
                if (oldestKey == null || oldestEntry == null) {
                    return;
                }
                if (this.cachedEntries.remove(oldestKey, oldestEntry)) {
                    this.cachedBytes.addAndGet(-oldestEntry.byteSize);
                    ResourceLifecycleStats.onAudioTrackReleased(null, oldestEntry.byteSize);
                }
            }
        }

        private static int maxCacheBytes() {
            try {
                return GeneralConfig.AUDIO_CACHE_MAX_BYTES == null ? 64 * 1024 * 1024 : GeneralConfig.AUDIO_CACHE_MAX_BYTES.get();
            } catch (IllegalStateException e) {
                return 64 * 1024 * 1024;
            }
        }

        private static final class CachedAudioEntry {
            final ByteBuffer audioData;
            final AudioFormat audioFormat;
            final IntArrayList seekPositions;
            final int byteSize;
            volatile long lastUsedAt;

            CachedAudioEntry(ByteBuffer audioData, AudioFormat audioFormat, IntArrayList seekPositions, int byteSize, long lastUsedAt) {
                this.audioData = audioData;
                this.audioFormat = audioFormat;
                this.seekPositions = seekPositions;
                this.byteSize = byteSize;
                this.lastUsedAt = lastUsedAt;
            }
        }
    }
}
