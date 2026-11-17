package com.micaftic.morpher.client.render;

import com.micaftic.morpher.core.gpu.OpenGlGpuRenderBackend;
import com.micaftic.morpher.core.render.RenderBackend;

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
