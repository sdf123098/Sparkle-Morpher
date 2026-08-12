package com.micaftic.morpher.core.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.core.render.RenderBackend;
import com.micaftic.morpher.core.render.RenderBackendDecision;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;

/**
 * R10.1 RenderBackend 实现：Blaze3D GPU 路径（future Blaze3D 后端），
 * 委托 {@link Blaze3DRenderPath}。未启用/不适用时返回 false（协调层回退）。
 */
public final class Blaze3DGpuRenderBackend implements RenderBackend {

    public static final Blaze3DGpuRenderBackend INSTANCE = new Blaze3DGpuRenderBackend();

    private Blaze3DGpuRenderBackend() {
    }

    @Override
    public RenderBackendDecision.Backend backend() {
        return RenderBackendDecision.Backend.BLAZE3D_GPU;
    }

    @Override
    public boolean tryRender(RenderRequest request) {
        GeoModel model = request.model();
        PoseStack.Pose pose = request.pose();
        return Blaze3DRenderPath.tryRender(
                model, pose, request.boneParams(), request.stateBuffer(), request.renderPartMask(),
                request.packedLight(), request.packedOverlay(),
                request.red(), request.green(), request.blue(), request.alpha(),
                request.textureLocation(), request.translucentTexture());
    }
}
