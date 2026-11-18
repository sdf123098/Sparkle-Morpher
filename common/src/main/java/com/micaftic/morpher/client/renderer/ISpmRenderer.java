package com.micaftic.morpher.client.renderer;
import com.micaftic.morpher.client.render.ModelRendererBridge;

import com.micaftic.morpher.client.renderer.CustomEntityTranslucentRenderType;
import com.micaftic.morpher.client.core.HostAnimatable;
import com.micaftic.morpher.client.render.ModelScenePose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ISpmRenderer<T extends HostAnimatable<?>> {
    MultiBufferSource getCurrentRTB();

    default void setCurrentRTB(MultiBufferSource bufferSource) {
    }

    default void renderWithBone(ModelScenePose model, T animatable, float partialTick, PoseStack poseStack, @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer vertexConsumer, int packedLight, int packedOverlayIn, float red, float green, float blue, float alpha) {
        setCurrentRTB(bufferSource);
        renderEarly(animatable, poseStack, partialTick, bufferSource, vertexConsumer, packedLight, packedOverlayIn, red, green, blue, alpha);
        renderLate(animatable, poseStack, partialTick, bufferSource, vertexConsumer, packedLight, packedOverlayIn, red, green, blue, alpha);
    }

    default void renderWithBoneAndRenderType(ModelScenePose model, T animatable, float partialTick, RenderType renderType, PoseStack poseStack, @Nullable MultiBufferSource bufferSource, int i, @Nullable VertexConsumer vertexConsumer, int i2, int i3, float f2, float f3, float f4, float f5) {
        if (vertexConsumer == null) {
            vertexConsumer = bufferSource.getBuffer(renderType);
        }
        animatable.resetAnimationState();
        ResourceLocation tex = animatable.getTextureLocation();
        ModelRendererBridge.renderMesh(vertexConsumer, poseStack.last(), model.getGeoModel(), model.getMatrixData(), model.getAbsPivotData(), i, 0, i2, i3, f2, f3, f4, f5, tex);
        setCurrentModelRenderCycle(RenderCycle.REPEATED);
    }

    default void renderEarly(T animatable, PoseStack poseStack, float partialTick,
                             @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, int packedLight,
                             int packedOverlayIn, float red, float green, float blue, float alpha) {
        if (getCurrentModelRenderCycle() == RenderCycle.INITIAL) {
            float width = animatable.getHeightScale();
            float height = animatable.getWidthScale();
            poseStack.scale(width, height, width);
        }
    }

    default void renderLate(T animatable, PoseStack poseStack, float partialTick, MultiBufferSource bufferSource,
                            @Nullable VertexConsumer buffer, int packedLight, int packedOverlayIn, float red, float green, float blue,
                            float alpha) {
    }

    @Nullable
    default RenderType getRenderType(ResourceLocation resourceLocation, boolean z, boolean z2, boolean z3) {
        if (z) {
            if (z3) {
                return CustomEntityTranslucentRenderType.get(resourceLocation);
            }
            return RenderType.entityCutoutNoCull(resourceLocation);
        }
        if (z2) {
            return RenderType.outline(resourceLocation);
        }
        return null;
    }

    default int getRenderColor(T animatable, float partialTick, PoseStack poseStack, @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, int packedLight) {
        return 0xFFFFFFFF;
    }

    @NotNull
    default RenderCycle getCurrentModelRenderCycle() {
        return RenderCycle.INITIAL;
    }

    default void setCurrentModelRenderCycle(RenderCycle cycle) {
    }
}
