package com.micaftic.morpher.audio;

import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.core.config.ConfigPolicies;
import com.micaftic.morpher.util.ResourceLifecycleStats;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * R10.3 Audio cache：per-assembly provider ownership + weighted LRU。
 *
 * <p>ownership 语义：{@link CachedAudioStreamProvider} 归 {@link ModelAssembly} 所有——
 * {@link #getOrCreateProvider} 建立关联，模型释放路径（{@code ClientModelManager}
 * releaseModelAssembly / unloadModelRuntime）调用 {@link #clearForModel} 立即清空并解除关联；
 * provider 内缓存随模型消亡，无跨会话残留。全局预算超限时按
 * {@link AudioEvictionPolicy}（weighted LRU：未使用时长 × 字节数）跨 provider 驱逐。</p>
 *
 * <p>解码说明：本模块无独立解码线程——音频解码在 SoundEngine 的同步流读取内联执行
 * （MC 线程域），缓存构建（{@link AudioCacheBuilder}）随之内联，不引入新线程。</p>
 */
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

        private final PendingTrackClaims<AudioTrackData> pendingTracks = new PendingTrackClaims<>();

        private final AtomicLong cachedBytes = new AtomicLong();

        CachedAudioStreamProvider() {
        }

        public void cacheAudioData(AudioTrackData trackData, ByteBuffer byteBuffer, IntArrayList intArrayList) {
            int byteSize = retainedBytes(byteBuffer, intArrayList);
            long budget = maxCacheBytes();
            if (budget <= 0) {
                clearAll("audio cache disabled");
                this.pendingTracks.release(trackData);
                return;
            }
            if (byteSize <= 0 || byteSize > budget) {
                this.pendingTracks.release(trackData);
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
            this.pendingTracks.release(trackData);
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
            // S0.2 修复：contains(Object) 是 value 查询（value 恒为 LOCK sentinel），去重永远失效；
            // 改用 putIfAbsent 原子占位，同一音轨的 cache builder 至多创建一次。
            if (trackData.getDuration() / trackData.getSampleRate() <= 4 && this.pendingTracks.tryClaim(trackData)) {
                cacheBuilder = new AudioCacheBuilder(this, trackData);
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
                long now = System.currentTimeMillis();
                // 收集全局候选（各 provider 的全部缓存条目投影）+ 驱逐目标定位
                ArrayList<AudioEvictionPolicy.Candidate> candidates = new ArrayList<>();
                ArrayList<Object[]> owners = new ArrayList<>(); // [provider, AudioTrackData]
                synchronized (LOCK) {
                    for (CachedAudioStreamProvider provider : providerCache.values()) {
                        for (var entry : provider.cachedEntries.entrySet()) {
                            candidates.add(new AudioEvictionPolicy.Candidate(entry.getValue().lastUsedAt, entry.getValue().byteSize));
                            owners.add(new Object[]{provider, entry.getKey()});
                        }
                    }
                }
                // R10.3：weighted LRU——优先驱逐"又旧又大"的条目，每次释放更多字节
                int victim = AudioEvictionPolicy.selectVictim(now, candidates);
                if (victim < 0) {
                    return;
                }
                Object[] owner = owners.get(victim);
                CachedAudioStreamProvider provider = (CachedAudioStreamProvider) owner[0];
                AudioTrackData key = (AudioTrackData) owner[1];
                synchronized (LOCK) {
                    CachedAudioEntry entry = provider.cachedEntries.get(key);
                    if (entry != null && provider.cachedEntries.remove(key, entry)) {
                        provider.cachedBytes.addAndGet(-entry.byteSize);
                        releaseBytes(entry.byteSize);
                    }
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
                return ConfigPolicies.memory().audioCacheMaxBytes();
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
