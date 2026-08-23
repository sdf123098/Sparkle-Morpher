package com.micaftic.morpher.core.render;

import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.mixin.client.GpuDeviceAccessor;
import com.mojang.blaze3d.systems.GpuDevice;
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
        return cachedBackend != null ? cachedBackend : SmGraphicsBackend.UNKNOWN;
    }

    public static String reason() {
        if (cachedReason == null) {
            detect();
        }
        return cachedReason != null ? cachedReason : "backend detection pending (render device not created yet)";
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

        // The active graphics backend belongs to Minecraft. A mod config may
        // disable acceleration, but it must not make raw OpenGL run on a
        // Vulkan/D3D device selected by Minecraft.
        return false;
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

        try {
            GpuDevice device = RenderSystem.getDevice();
            if (device == null) {
                // Render device not created yet (CLIENT_STARTED / client setup runs
                // before the render thread initializes the GpuDevice). Do NOT cache:
                // re-detect on the next call once the device exists.
                cachedReason = "RenderSystem device is null (will re-detect)";
                return;
            }
            Object backend = ((GpuDeviceAccessor) device).sparkleMorpher$getBackend();
            String className = backend == null ? device.getClass().getName() : backend.getClass().getName();
            String normalized = className.toLowerCase(Locale.ROOT);
            if (normalized.contains("vulkan")) {
                cachedBackend = SmGraphicsBackend.VULKAN;
                cachedReason = "RenderSystem backend class: " + className;
                return;
            }
            if (normalized.contains("opengl") || normalized.contains(".gl") || normalized.contains("gl")) {
                cachedBackend = SmGraphicsBackend.OPENGL;
                cachedReason = "RenderSystem backend class: " + className;
                return;
            }
            // Unknown backend (e.g. D3D12): stable for the session, cache it so the
            // hot path does not re-run detection every frame.
            cachedBackend = SmGraphicsBackend.UNKNOWN;
            cachedReason = "unknown RenderSystem backend class: " + className;
        } catch (Throwable t) {
            cachedBackend = SmGraphicsBackend.UNKNOWN;
            cachedReason = "RenderSystem device unavailable before init: " + t.getClass().getSimpleName();
        }
    }

}
