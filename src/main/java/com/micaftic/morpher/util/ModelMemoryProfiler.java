package com.micaftic.morpher.util;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.config.GeneralConfig;

public final class ModelMemoryProfiler {
    private ModelMemoryProfiler() {
    }

    public static boolean enabled() {
        return GeneralConfig.safeGet(GeneralConfig.MODEL_MEMORY_PROFILER, false);
    }

    public static void log(String stage, String modelId) {
        if (!enabled()) {
            return;
        }
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        YesSteveModel.LOGGER.info("[SM][Memory] {} model={} heapUsed={} MiB heapTotal={} MiB heapMax={} MiB",
                stage,
                modelId == null ? "-" : modelId,
                toMiB(used),
                toMiB(runtime.totalMemory()),
                toMiB(runtime.maxMemory()));
    }

    public static void logBytes(String stage, String modelId, byte[] bytes) {
        if (!enabled()) {
            return;
        }
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        YesSteveModel.LOGGER.info("[SM][Memory] {} model={} bytes={} KiB heapUsed={} MiB",
                stage,
                modelId == null ? "-" : modelId,
                bytes == null ? 0 : bytes.length / 1024,
                toMiB(used));
    }

    private static long toMiB(long bytes) {
        return bytes / (1024L * 1024L);
    }
}
