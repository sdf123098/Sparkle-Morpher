package com.micaftic.morpher.audio;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * R0/S0.2 characterization：并发 createAudioStream 的 pending 去重。
 *
 * 观察原理：生产代码通过 PendingTrackClaims.tryClaim 原子占位，
 * 同一音轨的并发请求只能有一个线程取得 builder 创建权。
 *
 * 修复前：pendingTracks.contains(trackData) 是 value 查询（value 恒为 LOCK sentinel），
 * 去重永远失效 → N 个线程创建 N 个 builder（delta = N * 400）。
 * 修复后：putIfAbsent 原子占位 → 仅 1 个 builder（delta = 400）。
 * 该测试只依赖纯 Java pending registry，避免 NeoForge 1.21.1 的客户端音频类
 * 在 JVM 单测运行时不可用时把并发契约测试误报为类路径失败。
 */
class AudioStreamCachePendingDedupeTest {

    private static final int THREADS = 16;
    @Test
    void concurrentClaims_buildCacheAtMostOnce() throws Exception {
        PendingTrackClaims<String> pending = new PendingTrackClaims<>();
        String track = "test-track";
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
                    if (pending.tryClaim(track)) {
                        completed.incrementAndGet();
                    }
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
        assertEquals(1, completed.get(), "同一音轨只能有一个线程获得 cache builder 创建权");
    }
}
