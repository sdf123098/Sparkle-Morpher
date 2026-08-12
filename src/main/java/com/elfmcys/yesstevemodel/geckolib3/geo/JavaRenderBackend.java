package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.core.render.RenderBackend;

/**
 * R10.1 RenderBackend 实现：Java 路径（1.21.1 分支版，VertexConsumer 顶点提交），
 * 委托 {@link ModelRendererBridge#renderModel}。恒处理（true）——兜底后端。
 */
public final class JavaRenderBackend implements RenderBackend {

    public static final JavaRenderBackend INSTANCE = new JavaRenderBackend();

    private JavaRenderBackend() {
    }

    @Override
    public boolean tryRender(RenderRequest request) {
        GeoModel model = request.model();
        ModelRendererBridge.renderModel(
                request.buffer(), request.pose(), request.projectionModelViewMatrix(),
                request.isCompatMode(), model, request.boneParams(), request.stateBuffer(),
                request.textureIndex(), request.renderPartMask(),
                request.packedLight(), request.packedOverlay(),
                request.red(), request.green(), request.blue(), request.alpha(),
                request.isPreview(), request.pass());
        return true;
    }
}
