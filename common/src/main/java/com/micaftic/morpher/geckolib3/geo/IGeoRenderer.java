package com.micaftic.morpher.geckolib3.geo;
import com.elfmcys.yesstevemodel.geckolib3.geo.NativeModelRenderer;

import com.micaftic.morpher.client.renderer.SubmitRenderContext;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.entity.GeckoVehicleEntity;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.util.Color;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.micaftic.morpher.geckolib3.util.EModelRenderCycle;
import com.micaftic.morpher.geckolib3.util.IRenderCycle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public interface IGeoRenderer<T extends AnimatableEntity<?>> {
    MultiBufferSource getCurrentRTB();

    default void setCurrentRTB(MultiBufferSource bufferSource) {
    }

    default void renderWithBone(AnimatedGeoModel model, T animatable, float partialTick, PoseStack poseStack, @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer vertexConsumer, int packedLight, int packedOverlayIn, float red, float green, float blue, float alpha) {
        setCurrentRTB(bufferSource);
        renderEarly(animatable, poseStack, partialTick, bufferSource, vertexConsumer, packedLight, packedOverlayIn, red, green, blue, alpha);
        renderLate(animatable, poseStack, partialTick, bufferSource, vertexConsumer, packedLight, packedOverlayIn, red, green, blue, alpha);
    }

    default void renderWithBoneAndRenderType(AnimatedGeoModel model, T animatable, float partialTick, RenderType renderType, PoseStack poseStack, @Nullable MultiBufferSource bufferSource, int i, @Nullable VertexConsumer vertexConsumer, int i2, int i3, float f2, float f3, float f4, float f5) {
        renderWithBoneAndRenderType(model, animatable, partialTick, renderType, poseStack, bufferSource, i, vertexConsumer, i2, i3, f2, f3, f4, f5, animatable.getTextureLocation());
    }

    default void renderWithBoneAndRenderType(AnimatedGeoModel model, T animatable, float partialTick, RenderType renderType, PoseStack poseStack, @Nullable MultiBufferSource bufferSource, int i, @Nullable VertexConsumer vertexConsumer, int i2, int i3, float f2, float f3, float f4, float f5, Identifier textureLocation) {
        SubmitNodeCollector collector = SubmitRenderContext.get();
        boolean allowDirectGpuRenderer = !(animatable instanceof GeckoVehicleEntity);
        if (collector != null && vertexConsumer == null) {
            animatable.resetAnimationState();
            float[] matrixData = Arrays.copyOf(model.getMatrixData(), model.getMatrixData().length);
            float[] absPivotData = Arrays.copyOf(model.getAbsPivotData(), model.getAbsPivotData().length);
            boolean previewMode = ModelPreviewRenderer.isPreview();
            boolean extraPlayerMode = ModelPreviewRenderer.isExtraPlayer();
            boolean worldRenderMode = ModelPreviewRenderer.isWorldRender();
            collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) ->
                    renderSubmittedGeometry(buffer, pose, model, matrixData, absPivotData, i, i2, i3, f2, f3, f4, f5, textureLocation, previewMode, extraPlayerMode, worldRenderMode, allowDirectGpuRenderer));
            setCurrentModelRenderCycle(EModelRenderCycle.REPEATED);
            return;
        }
        if (vertexConsumer == null) {
            vertexConsumer = bufferSource.getBuffer(renderType);
        }
        animatable.resetAnimationState();
        NativeModelRenderer.renderMesh(vertexConsumer, poseStack.last(), model.getGeoModel(), model.getMatrixData(), model.getAbsPivotData(), i, 0, i2, i3, f2, f3, f4, f5, textureLocation, allowDirectGpuRenderer);
        setCurrentModelRenderCycle(EModelRenderCycle.REPEATED);
    }

    private static void renderSubmittedGeometry(VertexConsumer buffer, PoseStack.Pose pose, AnimatedGeoModel model, float[] matrixData, float[] absPivotData, int textureIndex, int packedLight, int packedOverlay, float red, float green, float blue, float alpha, Identifier textureLocation, boolean previewMode, boolean extraPlayerMode, boolean worldRenderMode, boolean allowDirectGpuRenderer) {
        boolean previousPreviewMode = ModelPreviewRenderer.isPreview();
        boolean previousExtraPlayerMode = ModelPreviewRenderer.isExtraPlayer();
        boolean previousWorldRenderMode = ModelPreviewRenderer.isWorldRender();
        ModelPreviewRenderer.setPreviewMode(previewMode);
        ModelPreviewRenderer.setExtraPlayerMode(extraPlayerMode);
        ModelPreviewRenderer.setWorldRenderMode(worldRenderMode);
        try {
            NativeModelRenderer.renderMesh(buffer, pose, model.getGeoModel(), matrixData, absPivotData, textureIndex, 0, packedLight, packedOverlay, red, green, blue, alpha, textureLocation, allowDirectGpuRenderer);
        } finally {
            ModelPreviewRenderer.setWorldRenderMode(previousWorldRenderMode);
            ModelPreviewRenderer.setExtraPlayerMode(previousExtraPlayerMode);
            ModelPreviewRenderer.setPreviewMode(previousPreviewMode);
        }
    }

    default void renderEarly(T animatable, PoseStack poseStack, float partialTick,
                             @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, int packedLight,
                             int packedOverlayIn, float red, float green, float blue, float alpha) {
        if (getCurrentModelRenderCycle() == EModelRenderCycle.INITIAL) {
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
    default RenderType getRenderType(Identifier Identifier, boolean z, boolean z2, boolean z3) {
        if (z) {
            if (z3) {
                return RenderTypes.entityTranslucent(Identifier);
            }
            return RenderTypes.entityCutout(Identifier);
        }
        if (z2) {
            return RenderTypes.outline(Identifier);
        }
        return null;
    }

    default Color getRenderColor(T animatable, float partialTick, PoseStack poseStack, @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, int packedLight) {
        return Color.WHITE;
    }

    @NotNull
    default IRenderCycle getCurrentModelRenderCycle() {
        return EModelRenderCycle.INITIAL;
    }

    default void setCurrentModelRenderCycle(IRenderCycle cycle) {
    }
}
