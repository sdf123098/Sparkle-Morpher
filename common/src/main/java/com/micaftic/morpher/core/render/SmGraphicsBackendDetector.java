package com.micaftic.morpher.core.render;

import com.micaftic.morpher.config.GeneralConfig;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.Locale;

public final class SmGraphicsBackendDetector {
    private static volatile SmGraphicsBackend cachedBackend;
    private static volatile String cachedReason;

    private SmGraphicsBackendDetector() {
    }

    public static SmGraphicsBackend currentBackend() {
        SmGraphicsBackend backend = cachedBackend;
        if (backend != null) {
            return backend;
        }
        detect();
        return cachedBackend;
    }

    public static String reason() {
        if (cachedReason == null) {
            detect();
        }
        return cachedReason;
    }

    public static boolean isRawOpenGlAllowed() {
        SmRenderBackendMode mode = GeneralConfig.safeGet(GeneralConfig.GRAPHICS_BACKEND_MODE, SmRenderBackendMode.AUTO);
        if (mode == SmRenderBackendMode.DISABLED_GPU_ACCELERATION || mode == SmRenderBackendMode.VANILLA_PIPELINE_ONLY) {
            return false;
        }

        SmGraphicsBackend backend = currentBackend();
        if (backend == SmGraphicsBackend.OPENGL) {
            return true;
        }

        boolean disableOnNonOpenGl = GeneralConfig.safeGet(GeneralConfig.DISABLE_RAW_OPENGL_ON_NON_OPENGL, true);
        return !disableOnNonOpenGl && mode == SmRenderBackendMode.OPENGL_LEGACY_COMPAT;
    }

    public static boolean isOpenGlLegacyGpuRendererEnabled() {
        return isRawOpenGlAllowed()
                && GeneralConfig.safeGet(GeneralConfig.ENABLE_OPENGL_LEGACY_GPU_RENDERER, false);
    }

    public static boolean isOpenGlGuiBlurEnabled() {
        return isRawOpenGlAllowed()
                && GeneralConfig.safeGet(GeneralConfig.ENABLE_OPENGL_GUI_BLUR, false);
    }

    public static synchronized void resetForTests() {
        cachedBackend = null;
        cachedReason = null;
    }

    private static synchronized void detect() {
        if (cachedBackend != null) {
            return;
        }

        String override = firstNonBlank(
                System.getProperty("sparkle_morpher.graphicsBackend"),
                System.getProperty("ysm.graphicsBackend"),
                System.getenv("SPARKLE_MORPHER_GRAPHICS_BACKEND"),
                System.getenv("YSM_GRAPHICS_BACKEND")
        );
        SmGraphicsBackend overridden = parseBackend(override);
        if (overridden != null) {
            cachedBackend = overridden;
            cachedReason = "forced by property/env: " + override;
            return;
        }

        try {
            Object device = RenderSystem.getDevice();
            if (device != null) {
                String className = device.getClass().getName();
                String normalized = className.toLowerCase(Locale.ROOT);
                if (normalized.contains("vulkan")) {
                    cachedBackend = SmGraphicsBackend.VULKAN;
                    cachedReason = "RenderSystem device class: " + className;
                    return;
                }
                if (normalized.contains("opengl") || normalized.contains(".gl") || normalized.contains("gl")) {
                    cachedBackend = SmGraphicsBackend.OPENGL;
                    cachedReason = "RenderSystem device class: " + className;
                    return;
                }
                cachedBackend = SmGraphicsBackend.UNKNOWN;
                cachedReason = "unknown RenderSystem device class: " + className;
                return;
            }
        } catch (Throwable t) {
            cachedBackend = SmGraphicsBackend.UNKNOWN;
            cachedReason = "RenderSystem device unavailable before init: " + t.getClass().getSimpleName();
            return;
        }

        cachedBackend = SmGraphicsBackend.UNKNOWN;
        cachedReason = "RenderSystem device is null";
    }

    private static SmGraphicsBackend parseBackend(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("opengl") || normalized.equals("gl")) {
            return SmGraphicsBackend.OPENGL;
        }
        if (normalized.equals("vulkan") || normalized.equals("vk")) {
            return SmGraphicsBackend.VULKAN;
        }
        if (normalized.equals("unknown")) {
            return SmGraphicsBackend.UNKNOWN;
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
