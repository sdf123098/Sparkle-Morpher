package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.micaftic.morpher.core.gpu.OpenGlGpuRenderBackend;
import com.micaftic.morpher.core.render.RenderBackend;

/**
 * R10.1 RenderBackend 访问器（1.21.1 分支版）——本分支无 Blaze3D/Backend 决策 enum，
 * 直接按后端取实现。
 */
public final class RenderBackends {

    private RenderBackends() {
    }

    public static RenderBackend gpu() {
        return OpenGlGpuRenderBackend.INSTANCE;
    }

    public static RenderBackend simd() {
        return NativeSimdRenderBackend.INSTANCE;
    }

    public static RenderBackend java() {
        return JavaRenderBackend.INSTANCE;
    }
}
