package com.micaftic.morpher.core.render;

import com.elfmcys.yesstevemodel.geckolib3.geo.ModelRendererBridge.BoneRenderPass;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * R10.1 RenderBackend：渲染后端统一入口（interface 隔离，1.21.1 分支版）。
 *
 * <p>本分支无 RenderBackendDecision/Blaze3D 路径：决策为内联布尔（useGpuRenderer/
 * canRenderSimd），渲染动作经本接口隔离（OpenGL GPU / Native SIMD / Java）。
 * tryRender 返回 false 表示本后端未处理，协调层负责回退。
 */
public interface RenderBackend {

    /** 尝试用本后端渲染；false = 本后端未处理，协调层回退到下一候选。 */
    boolean tryRender(RenderRequest request);

    /**
     * R10.2：释放本后端持有的、属于指定模型的 GPU 资源（租约 revoke）。默认 no-op——
     * 仅持有 GPU mesh 的后端（OpenGL）实现；Java/Native SIMD 无此类资源。
     */
    default void release(GeoModel model) {
    }

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
            ResourceLocation textureLocation,
            boolean translucentTexture,
            boolean isPreview,
            boolean isCompatMode,
            BoneRenderPass pass
    ) {
    }
}
