package com.micaftic.morpher.client.render;

import com.micaftic.morpher.core.render.RenderBackend;

public final class NativeSimdRenderBackend implements RenderBackend {

    public static final NativeSimdRenderBackend INSTANCE = new NativeSimdRenderBackend();

    private NativeSimdRenderBackend() {
    }

    @Override
    public boolean tryRender(RenderRequest request) {
        return ModelRendererBridge.nativeRenderModel(
                request.buffer(), request.pose(), request.projectionModelViewMatrix(),
                request.isCompatMode(), request.model(), request.boneParams(), request.stateBuffer(),
                request.textureIndex(), request.renderPartMask(),
                request.packedLight(), request.packedOverlay(),
                request.red(), request.green(), request.blue(), request.alpha(),
                request.isPreview());
    }
}
