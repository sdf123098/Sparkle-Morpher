package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.micaftic.morpher.core.gpu.Blaze3DGpuRenderBackend;
import com.micaftic.morpher.core.gpu.OpenGlGpuRenderBackend;
import com.micaftic.morpher.core.render.RenderBackend;
import com.micaftic.morpher.core.render.RenderBackendDecision;

/**
 * R10.1 RenderBackend 注册表：Backend 决策 → 具体实现。
 *
 * <p>BLAZE3D_GPU/GPU 实现位于 core/gpu（依赖 Blaze3D/Gpu 渲染路径），
 * NATIVE_SIMD/JAVA 实现与本类同包（依赖 ModelRendererBridge 内部入口）。
 */
public final class RenderBackends {

    private RenderBackends() {
    }

    public static RenderBackend get(RenderBackendDecision.Backend backend) {
        return switch (backend) {
            case BLAZE3D_GPU -> Blaze3DGpuRenderBackend.INSTANCE;
            case GPU -> OpenGlGpuRenderBackend.INSTANCE;
            case NATIVE_SIMD -> NativeSimdRenderBackend.INSTANCE;
            case JAVA -> JavaRenderBackend.INSTANCE;
        };
    }
}
