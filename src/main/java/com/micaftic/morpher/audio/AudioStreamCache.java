package com.micaftic.morpher.audio;

import com.micaftic.morpher.ResourceCleanupHelper;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.util.ResourceLifecycleStats;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
            Minecraft.getInstance().execute(() -> {
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
        WeakReference<CachedAudioStreamProvider> weakReference = providerCache.remove(renderContext);
        CachedAudioStreamProvider provider = weakReference == null ? null : weakReference.get();
        if (provider != null) {
            provider.clear("model released");
        }
    }

    public static void clearAll(String reason) {
        for (WeakReference<CachedAudioStreamProvider> weakReference : new ArrayList<>(providerCache.values())) {
            CachedAudioStreamProvider provider = weakReference.get();
            if (provider != null) {
                provider.clear(reason);
            }
        }
        providerCache.clear();
    }

    public static class CachedAudioStreamProvider implements IAudioStreamProvider {

        private final ConcurrentHashMap<AudioTrackData, CachedAudioEntry> cachedEntries = new ConcurrentHashMap<>();

        private final ConcurrentHashMap<AudioTrackData, Object> pendingTracks = new ConcurrentHashMap<>();

        CachedAudioStreamProvider() {
        }

        public void cacheAudioData(AudioTrackData trackData, ByteBuffer byteBuffer, IntArrayList intArrayList) {
            CachedAudioEntry previous = this.cachedEntries.put(trackData, new CachedAudioEntry(byteBuffer, new AudioFormat(trackData.getSampleRate(), 16, 1, true, false), intArrayList));
            if (previous != null) {
                previous.release();
            }
            ResourceLifecycleStats.onAudioTrackCached(null, byteBuffer.capacity());
            this.pendingTracks.remove(trackData);
            trimToBudget();
        }

        @Override
        public IAudioStreamSupport createAudioStream(AudioTrackData trackData) throws UnsupportedAudioFileException, IOException {
            AudioCacheBuilder cacheBuilder;
            CachedAudioEntry audioEntry = this.cachedEntries.get(trackData);
            if (audioEntry != null) {
                audioEntry.touch();
                return new SeekableAudioStream(audioEntry.audioData.duplicate(), audioEntry.seekPositions, audioEntry.audioFormat);
            }
            if (trackData.getData() == null) {
                throw new UnsupportedAudioFileException();
            }
            if (trackData.getDuration() / trackData.getSampleRate() <= 4 && !this.pendingTracks.contains(trackData)) {
                cacheBuilder = new AudioCacheBuilder(this, trackData);
                this.pendingTracks.put(trackData, AudioStreamCache.LOCK);
            } else {
                cacheBuilder = null;
            }
            return switch (trackData.getCodec()) {
                case VORBIS -> new OggVorbisAudioStream(trackData.getData(), cacheBuilder);
                case OPUS -> new OggOpusAudioStream(trackData.getData(), cacheBuilder);
                default -> throw new UnsupportedAudioFileException();
            };
        }

        private void trimToBudget() {
            int budget = GeneralConfig.safeInt(GeneralConfig.AUDIO_CACHE_MAX_BYTES, 64 * 1024 * 1024);
            if (budget <= 0) {
                clear("audio cache disabled");
                return;
            }
            long total = 0L;
            for (CachedAudioEntry entry : cachedEntries.values()) {
                total += entry.byteSize;
            }
            while (total > budget && !cachedEntries.isEmpty()) {
                Map.Entry<AudioTrackData, CachedAudioEntry> oldest = null;
                for (Map.Entry<AudioTrackData, CachedAudioEntry> entry : cachedEntries.entrySet()) {
                    if (oldest == null || entry.getValue().lastUsedAt < oldest.getValue().lastUsedAt) {
                        oldest = entry;
                    }
                }
                if (oldest == null) {
                    return;
                }
                if (cachedEntries.remove(oldest.getKey(), oldest.getValue())) {
                    total -= oldest.getValue().byteSize;
                    oldest.getValue().release();
                }
            }
        }

        public void clear(String reason) {
            for (CachedAudioEntry entry : cachedEntries.values()) {
                entry.release();
            }
            cachedEntries.clear();
            pendingTracks.clear();
        }

        private static final class CachedAudioEntry {
            final ByteBuffer audioData;
            final AudioFormat audioFormat;
            final IntArrayList seekPositions;
            final int byteSize;
            volatile long lastUsedAt;

            CachedAudioEntry(ByteBuffer audioData, AudioFormat audioFormat, IntArrayList seekPositions) {
                this.audioData = audioData;
                this.audioFormat = audioFormat;
                this.seekPositions = seekPositions;
                this.byteSize = audioData.capacity();
                touch();
            }

            void touch() {
                this.lastUsedAt = System.currentTimeMillis();
            }

            void release() {
                MemoryUtil.memFree(audioData);
                ResourceLifecycleStats.onAudioTrackReleased(null, byteSize);
            }
        }
    }
}
