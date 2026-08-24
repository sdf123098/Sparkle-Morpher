package com.micaftic.morpher.core.render;

import com.elfmcys.yesstevemodel.geckolib3.geo.ModelRendererBridge.BoneRenderPass;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

/**
 * R10.1/R10.2 RenderBackend：渲染后端统一入口（interface 隔离）。
 *
 * <p>把 Java / Native SIMD / OpenGL GPU / future Blaze3D 四类渲染路径通过接口隔离，
 * 决策（{@link RenderBackendDecision}）与执行解耦——调用方只按 Backend 取实现，
 * 各后端可独立替换/回退。tryRender 返回 false 表示本后端不适用/失败，协调层负责回退。
 *
 * <p>R10.2 补生命周期：{@link #release(GeoModel)} 让模型装配释放时经后端 revoke 其
 * GPU 资源租约（OpenGL 后端释放 mesh；无 GPU 资源的后端为 no-op），保证 mesh ownership
 * 与 model runtime 绑定，释放边界与渲染边界一致。
 */
public interface RenderBackend {

    RenderBackendDecision.Backend backend();

    /** 尝试用本后端渲染；false = 本后端未处理，协调层回退到下一候选。 */
    boolean tryRender(RenderRequest request);

    /**
     * 释放本后端持有的、属于指定模型的 GPU 资源（租约 revoke）。默认 no-op——
     * 仅持有 GPU mesh 的后端（OpenGL）实现；Java/Native SIMD/Blaze3D 无此类资源。
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
            Identifier textureLocation,
            boolean translucentTexture,
            boolean disableGlow,
            boolean isPreview,
            boolean isCompatMode,
            BoneRenderPass pass
    ) {
    }
}
