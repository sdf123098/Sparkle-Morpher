package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.config.GeneralConfig;

/**
 * Low-overhead aggregate timings for the experimental paper-doll renderer.
 * Enable with AnimationFrameProfiler in the client config or
 * {@code -Dsparklemorpher.profileExtraPlayer=true}. Values measure CPU time;
 * a long SSBO upload call is therefore a useful signal for a driver synchronization stall.
 */
public final class ExtraPlayerRenderProfiler {
    private static final int REPORT_INTERVAL_FRAMES = 120;

    private static long totalNanos;
    private static long clearNanos;
    private static long modelNanos;
    private static long batchNanos;
    private static long compositeNanos;
    private static long animationNanos;
    private static long boneNanos;
    private static long uploadNanos;
    private static long drawNanos;
    private static long maxTotalNanos;
    private static int frames;
    private static int redraws;
    private static int fullGpuFrames;
    private static int fallbackFrames;
    private static int gpuPasses;
    private static int fallbackPasses;

    private ExtraPlayerRenderProfiler() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean("sparklemorpher.profileExtraPlayer")
                || GeneralConfig.safeGet(GeneralConfig.ANIMATION_FRAME_PROFILER, false);
    }

    public static void recordClear(long nanos) {
        clearNanos += positive(nanos);
    }

    public static void recordModel(long nanos) {
        modelNanos += positive(nanos);
    }

    public static void recordBatch(long nanos) {
        batchNanos += positive(nanos);
    }

    public static void recordComposite(long nanos) {
        compositeNanos += positive(nanos);
    }

    public static void recordAnimationEvaluation(long nanos) {
        animationNanos += positive(nanos);
    }

    public static void recordBoneMatrices(long nanos) {
        boneNanos += positive(nanos);
    }

    public static void recordSsboUpload(long nanos) {
        uploadNanos += positive(nanos);
    }

    public static void recordDrawSubmission(long nanos) {
        drawNanos += positive(nanos);
    }

    public static void finishFrame(long nanos, boolean redrawn, boolean fullyGpu,
                                   int frameGpuPasses, int frameFallbackPasses) {
        if (!enabled()) {
            return;
        }
        long duration = positive(nanos);
        totalNanos += duration;
        maxTotalNanos = Math.max(maxTotalNanos, duration);
        frames++;
        if (redrawn) {
            redraws++;
            if (fullyGpu) {
                fullGpuFrames++;
            } else {
                fallbackFrames++;
            }
        }
        gpuPasses += Math.max(0, frameGpuPasses);
        fallbackPasses += Math.max(0, frameFallbackPasses);

        if (frames >= REPORT_INTERVAL_FRAMES) {
            double divisor = frames * 1_000_000.0;
            YesSteveModel.LOGGER.info(
                    "[SM-EXTRA-PROFILE] frames={} redraws={} fullGpuFrames={} fallbackFrames={} "
                            + "passes(gpu/fallback)={}/{} avgMs(total/clear/model/animation/bone/upload/draw/batch/composite)="
                            + "{}/{}/{}/{}/{}/{}/{}/{}/{} maxTotalMs={}",
                    frames, redraws, fullGpuFrames, fallbackFrames, gpuPasses, fallbackPasses,
                    round(totalNanos / divisor), round(clearNanos / divisor), round(modelNanos / divisor),
                    round(animationNanos / divisor), round(boneNanos / divisor), round(uploadNanos / divisor),
                    round(drawNanos / divisor), round(batchNanos / divisor), round(compositeNanos / divisor),
                    round(maxTotalNanos / 1_000_000.0));
            reset();
        }
    }

    private static long positive(long nanos) {
        return Math.max(0L, nanos);
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static void reset() {
        totalNanos = 0L;
        clearNanos = 0L;
        modelNanos = 0L;
        batchNanos = 0L;
        compositeNanos = 0L;
        animationNanos = 0L;
        boneNanos = 0L;
        uploadNanos = 0L;
        drawNanos = 0L;
        maxTotalNanos = 0L;
        frames = 0;
        redraws = 0;
        fullGpuFrames = 0;
        fallbackFrames = 0;
        gpuPasses = 0;
        fallbackPasses = 0;
    }
}
