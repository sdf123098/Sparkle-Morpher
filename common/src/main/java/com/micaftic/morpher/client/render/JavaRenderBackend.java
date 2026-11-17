package com.micaftic.morpher.client.render;

import com.micaftic.morpher.core.render.RenderBackend;

public final class JavaRenderBackend implements RenderBackend {

    public static final JavaRenderBackend INSTANCE = new JavaRenderBackend();

    private JavaRenderBackend() {
    }

    @Override
    public boolean tryRender(RenderRequest request) {
        ModelRendererBridge.renderModel(
                request.buffer(), request.pose(), request.projectionModelViewMatrix(),
                request.isCompatMode(), request.model(), request.boneParams(), request.stateBuffer(),
                request.textureIndex(), request.renderPartMask(),
                request.packedLight(), request.packedOverlay(),
                request.red(), request.green(), request.blue(), request.alpha(),
                request.isPreview(), request.pass());
        return true;
    }
}
