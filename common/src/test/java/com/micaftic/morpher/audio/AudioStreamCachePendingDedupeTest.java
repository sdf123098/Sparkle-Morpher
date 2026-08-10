package com.micaftic.morpher.audio;

import com.micaftic.morpher.util.ResourceLifecycleStats;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R0/S0.2 characterization：并发 createAudioStream 的 pending 去重。
 *
 * 观察原理：AudioCacheBuilder 构造会调用 ResourceLifecycleStats.onDirectBufferAllocated
 * （estimatedCapacity = duration * 2）。用 UNDEFINED codec（构造参数 i=0）让 createAudioStream
 * 在创建 builder 后立即抛 UnsupportedAudioFileException，使并发窗口可预测——
 * 创建了多少个 builder 直接反映为 directBufferAllocated 的增量。
 *
 * 修复前：pendingTracks.contains(trackData) 是 value 查询（value 恒为 LOCK sentinel），
 * 去重永远失效 → N 个线程创建 N 个 builder（delta = N * 400）。
 * 修复后：putIfAbsent 原子占位 → 仅 1 个 builder（delta = 400）。
 * 断言 delta <= 800（容忍一次极端调度竞态下的二次创建），修复前必然失败。
 */
class AudioStreamCachePendingDedupeTest {

    private static final int THREADS = 16;
    private static final long ESTIMATED_CAPACITY = 400L; // duration(200ms) * 2

    @Test
    void concurrentCreateAudioStream_buildsCacheAtMostOnce() throws Exception {
        AudioStreamCache.CachedAudioStreamProvider provider = new AudioStreamCache.CachedAudioStreamProvider();
        AudioTrackData track = new AudioTrackData(ByteBuffer.wrap(new byte[]{1, 2, 3}), 0, 44100, 200L);

        long before = ResourceLifecycleStats.directBufferAllocatedBytesEstimate();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    try {
                        provider.createAudioStream(track);
                    } catch (UnsupportedAudioFileException expected) {
                        // UNDEFINED codec 必然抛出，属于预期路径
                    }
                    completed.incrementAndGet();
                } catch (Throwable t) {
                    failure.set(t);
                }
            });
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS), "并发线程未就绪");
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS), "并发线程未在限时内完成");

        assertNull(failure.get(), "存在未预期异常");
        assertEquals(THREADS, completed.get(), "所有线程都应完成调用");

        long delta = ResourceLifecycleStats.directBufferAllocatedBytesEstimate() - before;
        assertTrue(delta <= ESTIMATED_CAPACITY * 2,
                "pending 去重失效：创建了 " + (delta / ESTIMATED_CAPACITY) + " 个 cache builder（应为 1 个）");
        assertTrue(delta > 0, "应至少创建 1 个 cache builder");
    }
}
