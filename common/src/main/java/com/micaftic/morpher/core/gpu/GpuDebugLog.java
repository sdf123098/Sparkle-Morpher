package com.micaftic.morpher.core.gpu;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.config.GeneralConfig;
import org.lwjgl.opengl.GL11;

import java.util.concurrent.atomic.AtomicLong;

public final class GpuDebugLog {
    private static final boolean ENABLED = Boolean.getBoolean("sm.gpu.debug");
    private static final boolean VERBOSE = Boolean.getBoolean("sm.gpu.debug.verbose");
    private static final AtomicLong frame = new AtomicLong();

    private GpuDebugLog() {
    }

    public static boolean enabled() {
        return ENABLED || GeneralConfig.safeGet(GeneralConfig.GPU_DEBUG_LOG, false);
    }

    public static boolean verbose() {
        return enabled() && (VERBOSE || GeneralConfig.safeGet(GeneralConfig.GPU_DEBUG_VERBOSE_LOG, false));
    }

    public static long nextFrame() {
        return frame.incrementAndGet();
    }

    public static void info(String message, Object... args) {
        if (enabled()) {
            YesSteveModel.LOGGER.info("[SM-GPU] " + message, args);
        }
    }

    public static void verbose(String message, Object... args) {
        if (verbose()) {
            YesSteveModel.LOGGER.info("[SM-GPU] " + message, args);
        }
    }

    public static void warn(String message, Object... args) {
        if (enabled()) {
            YesSteveModel.LOGGER.warn("[SM-GPU] " + message, args);
        }
    }

    public static void error(String message, Throwable throwable) {
        if (enabled()) {
            YesSteveModel.LOGGER.error("[SM-GPU] " + message, throwable);
        }
    }

    public static void glError(String stage) {
        if (!enabled()) {
            return;
        }
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) {
            YesSteveModel.LOGGER.warn("[SM-GPU] GL error after {}: 0x{}", stage, Integer.toHexString(error));
        }
    }
}
