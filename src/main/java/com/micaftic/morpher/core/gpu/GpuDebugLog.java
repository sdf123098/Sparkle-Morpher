package com.micaftic.morpher.core.gpu;

import com.micaftic.morpher.YesSteveModel;

/** Minimal GPU diagnostic bridge kept compatible with the 1.21.1 config surface. */
public final class GpuDebugLog {
    private GpuDebugLog() {
    }

    public static void error(String message, Throwable throwable) {
        YesSteveModel.LOGGER.error("[SM-GPU] " + message, throwable);
    }
}
