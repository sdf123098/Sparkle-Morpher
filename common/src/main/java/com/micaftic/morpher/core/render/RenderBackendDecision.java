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
            GeneralConfig.NativeSimdPolicy nativePolicy
    ) {
        boolean isPreview = ModelPreviewRenderer.isPreview() || ModelPreviewRenderer.isExtraPlayer();
        boolean firstPerson = ModelPreviewRenderer.isFirstPerson();
        boolean worldRender = ModelPreviewRenderer.isWorldRender();
        boolean hasSubmitContext = SubmitRenderContext.get() != null;
        boolean compatibilityRenderer = GeneralConfig.USE_COMPATIBILITY_RENDERER.get();
        boolean gpuConfigured = GeneralConfig.USE_GPU_RENDERER.get();
        SmRenderBackendMode backendMode = GeneralConfig.safeGet(GeneralConfig.GRAPHICS_BACKEND_MODE, SmRenderBackendMode.AUTO);
        SmGraphicsBackend graphicsBackend = SmGraphicsBackendDetector.currentBackend();
        boolean conservativeRenderOnly = model != null && model.ensureConservativeRenderOnly();

        if (backendMode == SmRenderBackendMode.VANILLA_PIPELINE_ONLY) {
            return new RenderBackendDecision(Backend.JAVA, "java fallback: graphics backend mode VANILLA_PIPELINE_ONLY");
        }
        if (backendMode == SmRenderBackendMode.DISABLED_GPU_ACCELERATION) {
            return nativeOrJava(nativePolicy, conservativeRenderOnly, isPreview, firstPerson, translucentTexture, compatibilityRenderer, disableGlow, "raw OpenGL GPU acceleration disabled by backend mode");
        }
        if (!SmGraphicsBackendDetector.isRawOpenGlAllowed()) {
            return nativeOrJava(nativePolicy, conservativeRenderOnly, isPreview, firstPerson, translucentTexture, compatibilityRenderer, disableGlow,
                    "raw OpenGL disabled for mcBackend=" + graphicsBackend + " (" + SmGraphicsBackendDetector.reason() + ")");
        }
        if (!SmGraphicsBackendDetector.isOpenGlLegacyGpuRendererEnabled()) {
            return nativeOrJava(nativePolicy, conservativeRenderOnly, isPreview, firstPerson, translucentTexture, compatibilityRenderer, disableGlow,
                    "OpenGL legacy GPU renderer config disabled; mcBackend=" + graphicsBackend);
        }

        if (!allowDirectGpuRenderer) {
            return nativeOrJava(nativePolicy, conservativeRenderOnly, isPreview, firstPerson, translucentTexture, compatibilityRenderer, disableGlow, "gpu disabled by caller");
        }
        if (textureLocation == null) {
            return nativeOrJava(nativePolicy, conservativeRenderOnly, isPreview, firstPerson, translucentTexture, compatibilityRenderer, disableGlow, "gpu missing texture");
        }
        if (model.bakedBones == null || model.bakedBones.isEmpty()) {
            return new RenderBackendDecision(Backend.JAVA, "java fallback: no baked bones");
        }
        if (translucentTexture) {
            return nativeOrJava(nativePolicy, conservativeRenderOnly, isPreview, firstPerson, translucentTexture, compatibilityRenderer, disableGlow, "gpu unsupported translucent texture");
        }
        if (disableGlow) {
            return nativeOrJava(nativePolicy, conservativeRenderOnly, isPreview, firstPerson, translucentTexture, compatibilityRenderer, disableGlow, "gpu disabled by shaderpack glow compatibility");
        }
        if (hasSubmitContext) {
            return nativeOrJava(nativePolicy, conservativeRenderOnly, isPreview, firstPerson, translucentTexture, compatibilityRenderer, disableGlow, "gpu disabled inside submit render context");
        }
        if (!worldRender) {
            return nativeOrJava(nativePolicy, conservativeRenderOnly, isPreview, firstPerson, translucentTexture, compatibilityRenderer, disableGlow, "gpu disabled outside world render");
        }
        if (isPreview) {
            return nativeOrJava(nativePolicy, conservativeRenderOnly, isPreview, firstPerson, translucentTexture, compatibilityRenderer, disableGlow, "gpu disabled for preview");
        }
        if (firstPerson) {
            return nativeOrJava(nativePolicy, conservativeRenderOnly, isPreview, true, translucentTexture, compatibilityRenderer, disableGlow, "gpu disabled for first person");
        }
        if (compatibilityRenderer) {
            return new RenderBackendDecision(Backend.JAVA, "java fallback: compatibility renderer enabled");
        }
        if (!gpuConfigured) {
            return nativeOrJava(nativePolicy, conservativeRenderOnly, isPreview, firstPerson, translucentTexture, false, disableGlow, "gpu config disabled");
        }
        if (!GpuCapability.isAvailable()) {
            return nativeOrJava(nativePolicy, conservativeRenderOnly, isPreview, firstPerson, translucentTexture, false, disableGlow, "gpu unavailable: " + GpuCapability.getReason());
        }
        return new RenderBackendDecision(Backend.GPU, OculusCompat.isShaderPackInUse() ? "gpu iris path" : "gpu direct path");
    }

    private static RenderBackendDecision nativeOrJava(
            GeneralConfig.NativeSimdPolicy nativePolicy,
            boolean conservativeRenderOnly,
            boolean isPreview,
            boolean firstPerson,
            boolean translucentTexture,
            boolean compatibilityRenderer,
            boolean disableGlow,
            String gpuReason
    ) {
        // OFF: kill switch - always Java when the GPU path is not used.
        if (nativePolicy == GeneralConfig.NativeSimdPolicy.OFF) {
            return new RenderBackendDecision(Backend.JAVA, "java fallback: " + gpuReason + "; native SIMD policy OFF");
        }

        // AGGRESSIVE: prefer Native SIMD whenever the native runtime is loaded and
        // the compatibility renderer is disabled, except documented unsafe cases
        // kept on Java until validated (NATIVE_SIMD_26X_AGGRESSIVE_ROLLOUT_PLAN Phase 4).
        if (nativePolicy == GeneralConfig.NativeSimdPolicy.AGGRESSIVE) {
            if (compatibilityRenderer) {
                return new RenderBackendDecision(Backend.JAVA, "java fallback: compatibility renderer enabled");
            }
            if (!AccelerationCapability.canRenderSimd()) {
                return new RenderBackendDecision(Backend.JAVA, "java fallback: " + gpuReason + "; native SIMD unavailable: " + AccelerationCapability.getReason());
            }
            if (isPreview) {
                return new RenderBackendDecision(Backend.JAVA, "java fallback: " + gpuReason + "; native SIMD disabled for preview (unvalidated)");
            }
            if (translucentTexture) {
                return new RenderBackendDecision(Backend.JAVA, "java fallback: " + gpuReason + "; native SIMD disabled for translucent texture (unvalidated)");
            }
            if (disableGlow) {
                return new RenderBackendDecision(Backend.JAVA, "java fallback: " + gpuReason + "; native SIMD disabled by shaderpack glow compatibility (unvalidated)");
            }
            // STRICT_FALLBACK validation can force Java at runtime; see NativeSimdValidator.
            if (NativeSimdValidator.shouldForceJavaForSession()) {
                return new RenderBackendDecision(Backend.JAVA, "java fallback: " + gpuReason + "; native SIMD disabled by STRICT_FALLBACK validation");
            }
            return new RenderBackendDecision(Backend.NATIVE_SIMD, "native SIMD aggressive path: " + gpuReason);
        }

        // SAFE: keep the current 26.x conservative gates.
        if (isPreview) {
            return new RenderBackendDecision(Backend.JAVA, "java fallback: " + gpuReason + "; native SIMD disabled for preview");
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
        if (NativeSimdValidator.shouldForceJavaForSession()) {
            return new RenderBackendDecision(Backend.JAVA, "java fallback: " + gpuReason + "; native SIMD disabled by STRICT_FALLBACK validation");
        }
        return new RenderBackendDecision(Backend.NATIVE_SIMD, "native SIMD safe path: " + gpuReason);
    }
}
