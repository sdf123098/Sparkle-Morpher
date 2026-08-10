package com.micaftic.morpher.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * R0.5 资源生命周期基线：ResourceLifecycleStats 计数正确性。
 *
 * 单测级基线：验证各计数器的语义（分配/释放/缓存/释放/live 计数）。
 * 游戏内完整基线（load 20 models / switch 100 次 / join-leave 的资源曲线）
 * 属于 R0.5 的运行时测量部分，依赖 MC 环境，由人工/整合包测试执行。
 */
class ResourceLifecycleStatsTest {

    @Test
    void directBuffer_countersTrackAllocateAndFree() {
        long beforeAlloc = ResourceLifecycleStats.directBufferAllocatedBytesEstimate();
        long beforeFree = ResourceLifecycleStats.directBufferFreedBytesEstimate();

        ResourceLifecycleStats.onDirectBufferAllocated(null, 100);
        ResourceLifecycleStats.onDirectBufferAllocated(null, 50);
        assertEquals(beforeAlloc + 150, ResourceLifecycleStats.directBufferAllocatedBytesEstimate());

        ResourceLifecycleStats.onDirectBufferFreed(null, 40);
        assertEquals(beforeFree + 40, ResourceLifecycleStats.directBufferFreedBytesEstimate());
    }

    @Test
    void audioTrack_cachedMinusReleasedEqualsLive() {
        long beforeCached = ResourceLifecycleStats.audioTrackCachedBytes();
        long beforeReleased = ResourceLifecycleStats.audioTrackReleasedBytes();

        ResourceLifecycleStats.onAudioTrackCached(null, 500);
        ResourceLifecycleStats.onAudioTrackCached(null, 300);
        assertEquals(beforeCached + 800, ResourceLifecycleStats.audioTrackCachedBytes());

        ResourceLifecycleStats.onAudioTrackReleased(null, 200);
        assertEquals(beforeCached + 600, ResourceLifecycleStats.audioTrackCachedBytes(),
                "cached 应随释放扣减");
        assertEquals(beforeReleased + 200, ResourceLifecycleStats.audioTrackReleasedBytes());
    }

    @Test
    void gpuMesh_liveCountAndBytesNeverNegative() {
        ResourceLifecycleStats.onGpuMeshCreated(null, 1024);
        assertEquals(1, ResourceLifecycleStats.gpuMeshLiveCount());
        assertEquals(1024, ResourceLifecycleStats.gpuMeshLiveBytesEstimate());

        ResourceLifecycleStats.onGpuMeshDisposed(null, 1024);
        assertEquals(0, ResourceLifecycleStats.gpuMeshLiveCount());
        assertEquals(0, ResourceLifecycleStats.gpuMeshLiveBytesEstimate());

        // 释放多于创建时不允许出现负数
        ResourceLifecycleStats.onGpuMeshDisposed(null, 512);
        assertEquals(0, ResourceLifecycleStats.gpuMeshLiveBytesEstimate());
    }

    @Test
    void modelAssembly_loadedEvictedCounters() {
        long beforeLoaded = ResourceLifecycleStats.modelAssemblyLoadedCount();
        ResourceLifecycleStats.onModelAssemblyLoaded(null);
        assertEquals(beforeLoaded + 1, ResourceLifecycleStats.modelAssemblyLoadedCount());
    }
}
