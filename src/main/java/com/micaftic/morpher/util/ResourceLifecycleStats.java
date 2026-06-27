package com.micaftic.morpher.util;

import com.micaftic.morpher.YesSteveModel;

import java.util.concurrent.atomic.AtomicLong;

public final class ResourceLifecycleStats {
    private static final AtomicLong gpuMeshCreated = new AtomicLong();
    private static final AtomicLong gpuMeshDisposed = new AtomicLong();
    private static final AtomicLong gpuMeshLiveCount = new AtomicLong();
    private static final AtomicLong gpuMeshLiveBytesEstimate = new AtomicLong();
    private static final AtomicLong textureUploaded = new AtomicLong();
    private static final AtomicLong textureClosed = new AtomicLong();
    private static final AtomicLong textureSourceBytesRetained = new AtomicLong();
    private static final AtomicLong textureSourceBytesReleased = new AtomicLong();
    private static final AtomicLong directBufferAllocatedBytesEstimate = new AtomicLong();
    private static final AtomicLong directBufferFreedBytesEstimate = new AtomicLong();
    private static final AtomicLong modelAssemblyLoaded = new AtomicLong();
    private static final AtomicLong modelAssemblyEvicted = new AtomicLong();
    private static final AtomicLong audioTrackCachedBytes = new AtomicLong();
    private static final AtomicLong audioTrackReleasedBytes = new AtomicLong();

    private ResourceLifecycleStats() {
    }

    public static void onGpuMeshCreated(String modelId, long estimatedBytes) {
        long created = gpuMeshCreated.incrementAndGet();
        long liveCount = gpuMeshLiveCount.incrementAndGet();
        long liveBytes = gpuMeshLiveBytesEstimate.addAndGet(Math.max(0L, estimatedBytes));
        log("gpuMeshCreated", modelId, "created={} liveCount={} estimatedBytes={} liveBytes={}",
                created, liveCount, estimatedBytes, liveBytes);
    }

    public static void onGpuMeshDisposed(String modelId, long estimatedBytes) {
        long disposed = gpuMeshDisposed.incrementAndGet();
        long liveCount = gpuMeshLiveCount.updateAndGet(value -> Math.max(0L, value - 1L));
        long liveBytes = gpuMeshLiveBytesEstimate.updateAndGet(value -> Math.max(0L, value - Math.max(0L, estimatedBytes)));
        log("gpuMeshDisposed", modelId, "disposed={} liveCount={} estimatedBytes={} liveBytes={}",
                disposed, liveCount, estimatedBytes, liveBytes);
    }

    public static void onTextureUploaded(String modelId, int width, int height, long sourceBytes) {
        long uploaded = textureUploaded.incrementAndGet();
        long retained = textureSourceBytesRetained.addAndGet(Math.max(0L, sourceBytes));
        log("textureUploaded", modelId, "uploaded={} width={} height={} sourceBytes={} retainedSourceBytes={}",
                uploaded, width, height, sourceBytes, retained);
    }

    public static void onTextureClosed(String modelId) {
        long closed = textureClosed.incrementAndGet();
        log("textureClosed", modelId, "closed={}", closed);
    }

    public static void onTextureSourceBytesReleased(String modelId, long sourceBytes) {
        long retained = textureSourceBytesRetained.updateAndGet(value -> Math.max(0L, value - Math.max(0L, sourceBytes)));
        long released = textureSourceBytesReleased.addAndGet(Math.max(0L, sourceBytes));
        log("textureSourceBytesReleased", modelId, "sourceBytes={} releasedSourceBytes={} retainedSourceBytes={}",
                sourceBytes, released, retained);
    }

    public static void onDirectBufferAllocated(String modelId, long estimatedBytes) {
        long allocated = directBufferAllocatedBytesEstimate.addAndGet(Math.max(0L, estimatedBytes));
        log("directBufferAllocated", modelId, "estimatedBytes={} allocatedBytes={}", estimatedBytes, allocated);
    }

    public static void onDirectBufferFreed(String modelId, long estimatedBytes) {
        long freed = directBufferFreedBytesEstimate.addAndGet(Math.max(0L, estimatedBytes));
        log("directBufferFreed", modelId, "estimatedBytes={} freedBytes={}", estimatedBytes, freed);
    }

    public static void onModelAssemblyLoaded(String modelId) {
        long loaded = modelAssemblyLoaded.incrementAndGet();
        log("modelAssemblyLoaded", modelId, "loaded={}", loaded);
    }

    public static void onModelAssemblyEvicted(String modelId) {
        long evicted = modelAssemblyEvicted.incrementAndGet();
        log("modelAssemblyEvicted", modelId, "evicted={}", evicted);
    }

    public static void onAudioTrackCached(String modelId, long bytes) {
        long cachedBytes = audioTrackCachedBytes.addAndGet(Math.max(0L, bytes));
        log("audioTrackCached", modelId, "bytes={} cachedBytes={}", bytes, cachedBytes);
    }

    public static void onAudioTrackReleased(String modelId, long bytes) {
        long releasedBytes = audioTrackReleasedBytes.addAndGet(Math.max(0L, bytes));
        long cachedBytes = audioTrackCachedBytes.updateAndGet(value -> Math.max(0L, value - Math.max(0L, bytes)));
        log("audioTrackReleased", modelId, "bytes={} releasedBytes={} cachedBytes={}", bytes, releasedBytes, cachedBytes);
    }

    public static long gpuMeshLiveCount() {
        return gpuMeshLiveCount.get();
    }

    public static long gpuMeshLiveBytesEstimate() {
        return gpuMeshLiveBytesEstimate.get();
    }

    private static void log(String stage, String modelId, String message, Object... args) {
        if (!ModelMemoryProfiler.enabled()) {
            return;
        }
        Object[] fullArgs = new Object[args.length + 2];
        fullArgs[0] = modelId == null ? "-" : modelId;
        fullArgs[1] = stage;
        System.arraycopy(args, 0, fullArgs, 2, args.length);
        YesSteveModel.LOGGER.info("[SM][Lifecycle] model={} event={} " + message, fullArgs);
    }
}
