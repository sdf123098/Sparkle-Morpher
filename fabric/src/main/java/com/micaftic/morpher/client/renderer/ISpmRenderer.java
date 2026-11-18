package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.client.compat.ClientRenderCompatibilityRegistry;
import com.micaftic.morpher.client.core.HostAnimatable;
import com.micaftic.morpher.client.entity.SpmVehicleEntity;
import com.micaftic.morpher.client.render.ModelRendererBridge;
import com.micaftic.morpher.client.render.ModelScenePose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public interface ISpmRenderer<T extends HostAnimatable<?>> {
    MultiBufferSource getCurrentRTB();

    default void setCurrentRTB(MultiBufferSource bufferSource) {
    }

    default void renderWithBone(ModelScenePose model, T animatable, float partialTick, PoseStack poseStack,
                                @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer vertexConsumer,
                                int packedLight, int packedOverlayIn, float red, float green, float blue, float alpha) {
        setCurrentRTB(bufferSource);
        renderEarly(animatable, poseStack, partialTick, bufferSource, vertexConsumer, packedLight, packedOverlayIn, red, green, blue, alpha);
        renderLate(animatable, poseStack, partialTick, bufferSource, vertexConsumer, packedLight, packedOverlayIn, red, green, blue, alpha);
    }

    default void renderWithBoneAndRenderType(ModelScenePose model, T animatable, float partialTick, RenderType renderType,
                                             PoseStack poseStack, @Nullable MultiBufferSource bufferSource, int textureIndex,
                                             @Nullable VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
                                             float red, float green, float blue, float alpha) {
        renderWithBoneAndRenderType(model, animatable, partialTick, renderType, poseStack, bufferSource, textureIndex,
                vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha, animatable.getTextureLocation());
    }

    default void renderWithBoneAndRenderType(ModelScenePose model, T animatable, float partialTick, RenderType renderType,
                                             PoseStack poseStack, @Nullable MultiBufferSource bufferSource, int textureIndex,
                                             @Nullable VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
                                             float red, float green, float blue, float alpha, Identifier textureLocation) {
        SubmitNodeCollector collector = SubmitRenderContext.get();
        boolean allowDirectGpuRenderer = !(animatable instanceof SpmVehicleEntity);
        boolean splitEmissiveBones = vertexConsumer == null && textureLocation != null
                && ClientRenderCompatibilityRegistry.requiresEmissiveBoneSplit()
                && ModelRendererBridge.shouldUseEmissiveBoneMaterial(model.getGeoModel());
        if (collector != null && vertexConsumer == null) {
            animatable.resetAnimationState();
            float[] matrixData = Arrays.copyOf(model.getMatrixData(), model.getMatrixData().length);
            float[] absPivotData = Arrays.copyOf(model.getAbsPivotData(), model.getAbsPivotData().length);
            boolean previewMode = ModelPreviewRenderer.isPreview();
            boolean extraPlayerMode = ModelPreviewRenderer.isExtraPlayer();
            boolean worldRenderMode = ModelPreviewRenderer.isWorldRender();
            ModelRendererBridge.BoneRenderPass basePass = splitEmissiveBones
                    ? ModelRendererBridge.BoneRenderPass.NON_GLOW
                    : ModelRendererBridge.BoneRenderPass.ALL;
            collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) ->
                    renderSubmittedGeometry(collector, buffer, pose, model, matrixData, absPivotData, textureIndex,
                            packedLight, packedOverlay, red, green, blue, alpha, textureLocation, previewMode,
                            extraPlayerMode, worldRenderMode, allowDirectGpuRenderer, basePass));
            if (splitEmissiveBones) {
                RenderType emissiveType = RenderTypes.entityTranslucentEmissive(textureLocation);
                collector.submitCustomGeometry(poseStack, emissiveType, (pose, buffer) ->
                        renderSubmittedGeometry(collector, buffer, pose, model, matrixData, absPivotData, textureIndex,
                                packedLight, packedOverlay, red, green, blue, alpha, textureLocation, previewMode,
                                extraPlayerMode, worldRenderMode, false, ModelRendererBridge.BoneRenderPass.GLOW));
            }
            setCurrentModelRenderCycle(RenderCycle.REPEATED);
            return;
        }
        animatable.resetAnimationState();
        if (splitEmissiveBones && bufferSource != null) {
            VertexConsumer baseBuffer = bufferSource.getBuffer(renderType);
            ModelRendererBridge.renderMeshPass(baseBuffer, poseStack.last(), model.getGeoModel(),
                    model.getMatrixData(), model.getAbsPivotData(), textureIndex, 0, packedLight, packedOverlay,
                    red, green, blue, alpha, textureLocation, false, ModelRendererBridge.BoneRenderPass.NON_GLOW);
            VertexConsumer emissiveBuffer = bufferSource.getBuffer(
                    RenderTypes.entityTranslucentEmissive(textureLocation));
            ModelRendererBridge.renderMeshPass(emissiveBuffer, poseStack.last(), model.getGeoModel(),
                    model.getMatrixData(), model.getAbsPivotData(), textureIndex, 0, packedLight, packedOverlay,
                    red, green, blue, alpha, textureLocation, false, ModelRendererBridge.BoneRenderPass.GLOW);
        } else {
            if (vertexConsumer == null) {
                vertexConsumer = bufferSource.getBuffer(renderType);
            }
            ModelRendererBridge.renderMesh(vertexConsumer, poseStack.last(), model.getGeoModel(),
                    model.getMatrixData(), model.getAbsPivotData(), textureIndex, 0, packedLight, packedOverlay,
                    red, green, blue, alpha, textureLocation, allowDirectGpuRenderer);
        }
        setCurrentModelRenderCycle(RenderCycle.REPEATED);
    }

    private static void renderSubmittedGeometry(SubmitNodeCollector collector, VertexConsumer buffer, PoseStack.Pose pose,
                                                ModelScenePose model, float[] matrixData, float[] absPivotData,
                                                int textureIndex, int packedLight, int packedOverlay,
                                                float red, float green, float blue, float alpha,
                                                Identifier textureLocation, boolean previewMode, boolean extraPlayerMode,
                                                boolean worldRenderMode, boolean allowDirectGpuRenderer,
                                                ModelRendererBridge.BoneRenderPass boneRenderPass) {
        SubmitNodeCollector previousSubmitContext = SubmitRenderContext.get();
        SubmitRenderContext.set(collector);
        boolean previousPreviewMode = ModelPreviewRenderer.isPreview();
        boolean previousExtraPlayerMode = ModelPreviewRenderer.isExtraPlayer();
        boolean previousWorldRenderMode = ModelPreviewRenderer.isWorldRender();
        ModelPreviewRenderer.setPreviewMode(previewMode);
        ModelPreviewRenderer.setExtraPlayerMode(extraPlayerMode);
        ModelPreviewRenderer.setWorldRenderMode(worldRenderMode);
        try {
            ModelRendererBridge.renderMeshPass(buffer, pose, model.getGeoModel(), matrixData,
                    absPivotData, textureIndex, 0, packedLight, packedOverlay, red, green, blue,
                    alpha, textureLocation, allowDirectGpuRenderer, boneRenderPass);
        } finally {
            ModelPreviewRenderer.setWorldRenderMode(previousWorldRenderMode);
            ModelPreviewRenderer.setExtraPlayerMode(previousExtraPlayerMode);
            ModelPreviewRenderer.setPreviewMode(previousPreviewMode);
            SubmitRenderContext.set(previousSubmitContext);
        }
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
                            @Nullable VertexConsumer buffer, int packedLight, int packedOverlayIn,
                            float red, float green, float blue, float alpha) {
    }

    @Nullable
    default RenderType getRenderType(Identifier texture, boolean visible, boolean glowing, boolean translucent) {
        if (visible) {
            if (translucent) {
                return RenderTypes.entityTranslucent(texture);
            }
            return RenderTypes.entityCutout(texture);
        }
        if (glowing) {
            return RenderTypes.outline(texture);
        }
        return null;
    }

    default int getRenderColor(T animatable, float partialTick, PoseStack poseStack,
                               @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, int packedLight) {
        return 0xFFFFFFFF;
    }

    @NotNull
    default RenderCycle getCurrentModelRenderCycle() {
        return RenderCycle.INITIAL;
    }

    default void setCurrentModelRenderCycle(RenderCycle cycle) {
    }
}
