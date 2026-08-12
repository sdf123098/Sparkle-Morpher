package com.micaftic.morpher.core.render;

import com.elfmcys.yesstevemodel.geckolib3.geo.ModelRendererBridge.BoneRenderPass;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

/**
 * R10.1 RenderBackend：渲染后端统一入口（interface 隔离）。
 *
 * <p>把 Java / Native SIMD / OpenGL GPU / future Blaze3D 四类渲染路径通过接口隔离，
 * 决策（{@link RenderBackendDecision}）与执行解耦——调用方只按 Backend 取实现，
 * 各后端可独立替换/回退。tryRender 返回 false 表示本后端不适用/失败，协调层负责回退。
 */
public interface RenderBackend {

    RenderBackendDecision.Backend backend();

    /** 尝试用本后端渲染；false = 本后端未处理，协调层回退到下一候选。 */
    boolean tryRender(RenderRequest request);

    /** 渲染请求参数（renderMeshPass 入参封装，各后端按需解包）。 */
    record RenderRequest(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            Matrix4f projectionModelViewMatrix,
            GeoModel model,
            float[] boneParams,
            float[] stateBuffer,
            int textureIndex,
            int renderPartMask,
            int packedLight,
            int packedOverlay,
            float red, float green, float blue, float alpha,
            Identifier textureLocation,
            boolean translucentTexture,
            boolean disableGlow,
            boolean isPreview,
            boolean isCompatMode,
            BoneRenderPass pass
    ) {
    }
}
