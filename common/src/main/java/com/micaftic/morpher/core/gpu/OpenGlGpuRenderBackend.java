package com.micaftic.morpher.core.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.core.render.RenderBackend;
import com.micaftic.morpher.core.render.RenderBackendDecision;
import com.mojang.blaze3d.vertex.PoseStack;

/**
 * R10.1 RenderBackend 实现：OpenGL GPU 路径，
 * 委托 {@link GpuRenderPath}。未启用/不适用时返回 false（协调层回退）。
 */
public final class OpenGlGpuRenderBackend implements RenderBackend {

    public static final OpenGlGpuRenderBackend INSTANCE = new OpenGlGpuRenderBackend();

    private OpenGlGpuRenderBackend() {
    }

    @Override
    public RenderBackendDecision.Backend backend() {
        return RenderBackendDecision.Backend.GPU;
    }

    @Override
    public boolean tryRender(RenderRequest request) {
        GeoModel model = request.model();
        PoseStack.Pose pose = request.pose();
        return GpuRenderPath.tryRender(
                model, pose, request.boneParams(), request.stateBuffer(), request.renderPartMask(),
                request.packedLight(), request.packedOverlay(),
                request.red(), request.green(), request.blue(), request.alpha(),
                request.textureLocation(), request.translucentTexture());
    }
}
