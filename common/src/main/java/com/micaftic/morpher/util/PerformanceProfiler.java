package com.micaftic.morpher.util;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.config.GeneralConfig;

public final class PerformanceProfiler {
    private PerformanceProfiler() {
    }

    public static boolean enabled() {
        return GeneralConfig.safeGet(GeneralConfig.MODEL_IMPORT_PERFORMANCE_LOG, false);
    }

    public static long start() {
        return enabled() ? System.nanoTime() : 0L;
    }

    public static void logElapsed(String stage, String modelId, long startNanos, String details) {
        if (startNanos == 0L || !enabled()) {
            return;
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        if (details == null || details.isBlank()) {
            YesSteveModel.LOGGER.info("[SM][Perf] {} model={} elapsed={} ms", stage, safe(modelId), elapsedMs);
        } else {
            YesSteveModel.LOGGER.info("[SM][Perf] {} model={} elapsed={} ms {}", stage, safe(modelId), elapsedMs, details);
        }
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
