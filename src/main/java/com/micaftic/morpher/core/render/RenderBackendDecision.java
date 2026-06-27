package com.micaftic.morpher.core.render;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.SubmitRenderContext;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.core.compat.oculus.OculusCompat;
import com.micaftic.morpher.core.gpu.GpuCapability;
import com.micaftic.morpher.core.acceleration.AccelerationCapability;

public final class RenderBackendDecision {
    public enum Backend {
        GPU,
        NATIVE_SIMD,
        JAVA
    }

    public final Backend backend;
    public final String reason;

    private RenderBackendDecision(Backend backend, String reason) {
        this.backend = backend;
        this.reason = reason;
    }

    public static RenderBackendDecision choose(
            GeoModel model,
            boolean allowDirectGpuRenderer,
            boolean translucentTexture,
            boolean disableGlow,
            Object textureLocation,
            boolean useNativeSimdRenderer
    ) {
        boolean isPreview = ModelPreviewRenderer.isPreview() || ModelPreviewRenderer.isExtraPlayer();
        boolean firstPerson = ModelPreviewRenderer.isFirstPerson();
        boolean worldRender = ModelPreviewRenderer.isWorldRender();
        boolean hasSubmitContext = SubmitRenderContext.get() != null;
        boolean compatibilityRenderer = GeneralConfig.USE_COMPATIBILITY_RENDERER.get();
        boolean gpuConfigured = GeneralConfig.USE_GPU_RENDERER.get();
        SmRenderBackendMode backendMode = GeneralConfig.safeGet(GeneralConfig.GRAPHICS_BACKEND_MODE, SmRenderBackendMode.AUTO);
        SmGraphicsBackend graphicsBackend = SmGraphicsBackendDetector.currentBackend();

        if (backendMode == SmRenderBackendMode.VANILLA_PIPELINE_ONLY) {
            return new RenderBackendDecision(Backend.JAVA, "java fallback: graphics backend mode VANILLA_PIPELINE_ONLY");
        }
        if (backendMode == SmRenderBackendMode.DISABLED_GPU_ACCELERATION) {
            return nativeOrJava(useNativeSimdRenderer, isPreview, firstPerson, translucentTexture, compatibilityRenderer, disableGlow, "raw OpenGL GPU acceleration disabled by backend mode");
        }
        if (!SmGraphicsBackendDetector.isRawOpenGlAllowed()) {
            return nativeOrJava(useNativeSimdRenderer, isPreview, firstPerson, translucentTexture, compatibilityRenderer, disableGlow,
                    "raw OpenGL disabled for mcBackend=" + graphicsBackend + " (" + SmGraphicsBackendDetector.reason() + ")");
        }
        if (!SmGraphicsBackendDetector.isOpenGlLegacyGpuRendererEnabled()) {
            return nativeOrJava(useNativeSimdRenderer, isPreview, firstPerson, translucentTexture, compatibilityRenderer, disableGlow,
                    "OpenGL legacy GPU renderer config disabled; mcBackend=" + graphicsBackend);
        }

        if (!allowDirectGpuRenderer) {
            return nativeOrJava(useNativeSimdRenderer, isPreview, firstPerson, translucentTexture, compatibilityRenderer, disableGlow, "gpu disabled by caller");
        }
        if (textureLocation == null) {
            return nativeOrJava(useNativeSimdRenderer, isPreview, firstPerson, translucentTexture, compatibilityRenderer, disableGlow, "gpu missing texture");
        }
        if (model.bakedBones == null || model.bakedBones.isEmpty()) {
            return new RenderBackendDecision(Backend.JAVA, "java fallback: no baked bones");
        }
        if (translucentTexture) {
            return nativeOrJava(useNativeSimdRenderer, isPreview, firstPerson, translucentTexture, compatibilityRenderer, disableGlow, "gpu unsupported translucent texture");
        }
        if (disableGlow) {
            return nativeOrJava(useNativeSimdRenderer, isPreview, firstPerson, translucentTexture, compatibilityRenderer, disableGlow, "gpu disabled by shaderpack glow compatibility");
        }
        if (hasSubmitContext) {
            return nativeOrJava(useNativeSimdRenderer, isPreview, firstPerson, translucentTexture, compatibilityRenderer, disableGlow, "gpu disabled inside submit render context");
        }
        if (!worldRender) {
            return nativeOrJava(useNativeSimdRenderer, isPreview, firstPerson, translucentTexture, compatibilityRenderer, disableGlow, "gpu disabled outside world render");
        }
        if (isPreview) {
            return nativeOrJava(useNativeSimdRenderer, isPreview, firstPerson, translucentTexture, compatibilityRenderer, disableGlow, "gpu disabled for preview");
        }
        if (firstPerson) {
            return nativeOrJava(useNativeSimdRenderer, isPreview, true, translucentTexture, compatibilityRenderer, disableGlow, "gpu disabled for first person");
        }
        if (compatibilityRenderer) {
            return new RenderBackendDecision(Backend.JAVA, "java fallback: compatibility renderer enabled");
        }
        if (!gpuConfigured) {
            return nativeOrJava(useNativeSimdRenderer, isPreview, firstPerson, translucentTexture, false, disableGlow, "gpu config disabled");
        }
        if (!GpuCapability.isAvailable()) {
            return nativeOrJava(useNativeSimdRenderer, isPreview, firstPerson, translucentTexture, false, disableGlow, "gpu unavailable: " + GpuCapability.getReason());
        }
        return new RenderBackendDecision(Backend.GPU, OculusCompat.isShaderPackInUse() ? "gpu iris path" : "gpu direct path");
    }

    private static RenderBackendDecision nativeOrJava(
            boolean useNativeSimdRenderer,
            boolean isPreview,
            boolean firstPerson,
            boolean translucentTexture,
            boolean compatibilityRenderer,
            boolean disableGlow,
            String gpuReason
    ) {
        if (!useNativeSimdRenderer) {
            return new RenderBackendDecision(Backend.JAVA, "java fallback: " + gpuReason + "; native SIMD config disabled");
        }
        if (isPreview) {
            return new RenderBackendDecision(Backend.JAVA, "java fallback: " + gpuReason + "; native SIMD disabled for preview");
        }
        if (firstPerson) {
            return new RenderBackendDecision(Backend.JAVA, "java fallback: " + gpuReason + "; native SIMD disabled for first person");
        }
        if (translucentTexture) {
            return new RenderBackendDecision(Backend.JAVA, "java fallback: " + gpuReason + "; native SIMD disabled for translucent texture");
        }
        if (compatibilityRenderer) {
            return new RenderBackendDecision(Backend.JAVA, "java fallback: compatibility renderer enabled");
        }
        if (disableGlow) {
            return new RenderBackendDecision(Backend.JAVA, "java fallback: " + gpuReason + "; native SIMD disabled by shaderpack glow compatibility");
        }
        if (!AccelerationCapability.canRenderSimd()) {
            return new RenderBackendDecision(Backend.JAVA, "java fallback: " + gpuReason + "; native SIMD unavailable: " + AccelerationCapability.getReason());
        }
        return new RenderBackendDecision(Backend.NATIVE_SIMD, "native SIMD path: " + gpuReason);
    }
}
