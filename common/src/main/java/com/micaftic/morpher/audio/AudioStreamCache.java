package com.micaftic.morpher.audio;

import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.util.ResourceLifecycleStats;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class AudioStreamCache {

    private static final IdentityHashMap<ModelAssembly, CachedAudioStreamProvider> providerCache = new IdentityHashMap<>();

    private static final Object LOCK = new Object();

    private static final AtomicLong globalCachedBytes = new AtomicLong();

    public static IAudioStreamProvider getOrCreateProvider(ModelAssembly renderContext) {
        RenderSystem.assertOnRenderThread();
        synchronized (LOCK) {
            CachedAudioStreamProvider existingProvider = providerCache.get(renderContext);
            if (existingProvider != null) {
                return existingProvider;
            }
            CachedAudioStreamProvider newProvider = new CachedAudioStreamProvider();
            providerCache.put(renderContext, newProvider);
            return newProvider;
        }
    }

    public static void clearForModel(ModelAssembly renderContext) {
        if (renderContext == null) {
            return;
        }
        synchronized (LOCK) {
            CachedAudioStreamProvider provider = providerCache.remove(renderContext);
            if (provider != null) {
                provider.clear("model evicted");
            }
        }
    }

    public static void clearAll(String reason) {
        synchronized (LOCK) {
            for (CachedAudioStreamProvider provider : providerCache.values()) {
                provider.clear(reason);
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
            int byteSize = retainedBytes(byteBuffer, intArrayList);
            long budget = maxCacheBytes();
            if (budget <= 0) {
                clearAll("audio cache disabled");
                this.pendingTracks.remove(trackData);
                return;
            }
            if (byteSize <= 0 || byteSize > budget) {
                this.pendingTracks.remove(trackData);
                return;
            }
            synchronized (LOCK) {
                CachedAudioEntry previous = this.cachedEntries.put(trackData, new CachedAudioEntry(byteBuffer, new AudioFormat(trackData.getSampleRate(), 16, 1, true, false), intArrayList, byteSize, System.currentTimeMillis()));
                if (previous != null) {
                    this.cachedBytes.addAndGet(-previous.byteSize);
                    releaseBytes(previous.byteSize);
                }
                this.cachedBytes.addAndGet(byteSize);
                globalCachedBytes.addAndGet(byteSize);
                ResourceLifecycleStats.onAudioTrackCached(null, byteSize);
                trimToBudget();
            }
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
            synchronized (LOCK) {
                this.cachedEntries.clear();
                this.pendingTracks.clear();
                releaseBytes(this.cachedBytes.getAndSet(0L));
            }
        }

        private void trimToBudget() {
            long budget = maxCacheBytes();
            while (globalCachedBytes.get() > budget) {
                CachedAudioStreamProvider oldestProvider = null;
                AudioTrackData oldestKey = null;
                CachedAudioEntry oldestEntry = null;
                for (CachedAudioStreamProvider provider : providerCache.values()) {
                    for (var entry : provider.cachedEntries.entrySet()) {
                        if (oldestEntry == null || entry.getValue().lastUsedAt < oldestEntry.lastUsedAt) {
                            oldestProvider = provider;
                            oldestKey = entry.getKey();
                            oldestEntry = entry.getValue();
                        }
                    }
                }
                if (oldestProvider == null || oldestKey == null || oldestEntry == null) {
                    return;
                }
                if (oldestProvider.cachedEntries.remove(oldestKey, oldestEntry)) {
                    oldestProvider.cachedBytes.addAndGet(-oldestEntry.byteSize);
                    releaseBytes(oldestEntry.byteSize);
                }
            }
        }

        private static int retainedBytes(ByteBuffer byteBuffer, IntArrayList seekPositions) {
            if (byteBuffer == null) {
                return 0;
            }
            long bytes = byteBuffer.capacity();
            if (seekPositions != null) {
                bytes += (long) seekPositions.size() * Integer.BYTES;
            }
            return (int) Math.min(Integer.MAX_VALUE, bytes);
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

    private static void releaseBytes(long released) {
        if (released <= 0L) {
            return;
        }
        globalCachedBytes.addAndGet(-released);
        ResourceLifecycleStats.onAudioTrackReleased(null, released);
    }
}
