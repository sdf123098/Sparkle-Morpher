package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.micaftic.morpher.core.config.ConfigPolicies;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.core.gpu.GpuDebugLog;
import com.micaftic.morpher.core.render.RenderBackend;
import com.micaftic.morpher.core.render.RenderBackendDecision;

/**
 * R10.1 RenderBackend 实现：Java 路径（VertexConsumer 顶点提交 + 矩阵计算），
 * 委托 {@link ModelRendererBridge#renderModel}。恒处理（true）——兜底后端。
 */
public final class JavaRenderBackend implements RenderBackend {

    public static final JavaRenderBackend INSTANCE = new JavaRenderBackend();

    private JavaRenderBackend() {
    }

    @Override
    public RenderBackendDecision.Backend backend() {
        return RenderBackendDecision.Backend.JAVA;
    }

    @Override
    public boolean tryRender(RenderRequest request) {
        GeoModel model = request.model();
        GpuDebugLog.verbose("entry rendered through Java model path texture={} nativeSimdPolicy={} translucent={} preview={} firstPerson={} compat={} disableGlow={}",
                request.textureLocation(),
                ConfigPolicies.graphics().nativeSimdPolicy(),
                request.translucentTexture(), request.isPreview(),
                com.micaftic.morpher.client.renderer.ModelPreviewRenderer.isFirstPerson(),
                ConfigPolicies.render().useCompatibilityRenderer(),
                request.disableGlow());
        ModelRendererBridge.renderModel(
                request.buffer(), request.pose(), request.projectionModelViewMatrix(),
                request.isCompatMode(), model, request.boneParams(), request.stateBuffer(),
                request.textureIndex(), request.renderPartMask(),
                request.packedLight(), request.packedOverlay(),
                request.red(), request.green(), request.blue(), request.alpha(),
                request.isPreview(), request.disableGlow(), request.pass());
        return true;
    }
}
