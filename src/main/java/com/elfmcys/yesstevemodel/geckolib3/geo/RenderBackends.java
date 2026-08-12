package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.micaftic.morpher.core.gpu.OpenGlGpuRenderBackend;
import com.micaftic.morpher.core.render.RenderBackend;
import com.micaftic.morpher.core.render.RenderBackendDecision;

/**
 * R10.1 RenderBackend 注册表：Backend 决策 → 具体实现（无 Blaze3D 路径的分支版）。
 *
 * <p>本分支无 Blaze3D GPU 后端（BLAZE3D_GPU 仅 26.2 分支存在），
 * NATIVE_SIMD/JAVA 实现与本类同包（依赖 ModelRendererBridge 内部入口）。
 */
public final class RenderBackends {

    private RenderBackends() {
    }

    public static RenderBackend get(RenderBackendDecision.Backend backend) {
        return switch (backend) {
            case GPU -> OpenGlGpuRenderBackend.INSTANCE;
            case NATIVE_SIMD -> NativeSimdRenderBackend.INSTANCE;
            case JAVA -> JavaRenderBackend.INSTANCE;
        };
    }
}
