package com.micaftic.morpher.geckolib3.geo;
import com.elfmcys.yesstevemodel.geckolib3.geo.ModelRendererBridge;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.renderer.SubmitRenderContext;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.compat.ClientRenderCompatibilityRegistry;
import com.micaftic.morpher.client.entity.GeckoVehicleEntity;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.util.Color;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.micaftic.morpher.geckolib3.util.EModelRenderCycle;
import com.micaftic.morpher.geckolib3.util.IRenderCycle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.micaftic.morpher.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import java.util.Arrays;
import org.jetbrains.annotations.Nullable;

public interface IGeoRenderer<T extends AnimatableEntity<?>> {
    ModelRenderDebug MODEL_RENDER_DEBUG = new ModelRenderDebug();

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
        // Splitting glow bones into a dedicated emissive RenderType pass disables the GPU/SIMD
        // fast paths (renderMeshPass only serves BoneRenderPass.ALL through them), so it is gated
        // behind an explicit renderer capability request instead of running for every player.
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
                    renderSubmittedGeometry(collector, buffer, pose, model, matrixData, absPivotData, i, i2, i3,
                            f2, f3, f4, f5, textureLocation, previewMode, extraPlayerMode,
                            worldRenderMode, allowDirectGpuRenderer, basePass));
            if (splitEmissiveBones) {
                RenderType emissiveType = RenderTypes.entityTranslucentEmissive(textureLocation);
                collector.submitCustomGeometry(poseStack, emissiveType, (pose, buffer) ->
                        renderSubmittedGeometry(collector, buffer, pose, model, matrixData, absPivotData, i, i2, i3,
                                f2, f3, f4, f5, textureLocation, previewMode, extraPlayerMode,
                                worldRenderMode, false, ModelRendererBridge.BoneRenderPass.GLOW));
            }
            setCurrentModelRenderCycle(EModelRenderCycle.REPEATED);
            return;
        }
        animatable.resetAnimationState();
        if (splitEmissiveBones && bufferSource != null) {
            VertexConsumer baseBuffer = bufferSource.getBuffer(renderType);
            ModelRendererBridge.renderMeshPass(baseBuffer, poseStack.last(), model.getGeoModel(),
                    model.getMatrixData(), model.getAbsPivotData(), i, 0, i2, i3, f2, f3, f4, f5,
                    textureLocation, false, ModelRendererBridge.BoneRenderPass.NON_GLOW);
            VertexConsumer emissiveBuffer = bufferSource.getBuffer(
                    RenderTypes.entityTranslucentEmissive(textureLocation));
            ModelRendererBridge.renderMeshPass(emissiveBuffer, poseStack.last(), model.getGeoModel(),
                    model.getMatrixData(), model.getAbsPivotData(), i, 0, i2, i3, f2, f3, f4, f5,
                    textureLocation, false, ModelRendererBridge.BoneRenderPass.GLOW);
        } else {
            if (vertexConsumer == null) {
                vertexConsumer = bufferSource.getBuffer(renderType);
            }
            ModelRendererBridge.renderMesh(vertexConsumer, poseStack.last(), model.getGeoModel(),
                    model.getMatrixData(), model.getAbsPivotData(), i, 0, i2, i3, f2, f3, f4, f5,
                    textureLocation, allowDirectGpuRenderer);
        }
        setCurrentModelRenderCycle(EModelRenderCycle.REPEATED);
    }

    private static void renderSubmittedGeometry(SubmitNodeCollector collector, VertexConsumer buffer, PoseStack.Pose pose,
            AnimatedGeoModel model, float[] matrixData, float[] absPivotData, int textureIndex,
            int packedLight, int packedOverlay, float red, float green, float blue, float alpha,
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

    final class ModelRenderDebug {
        private int debugLogCount;
        private long lastDebugLogMillis;

        void logSubmit(String path, AnimatableEntity<?> animatable, AnimatedGeoModel model, RenderType renderType, Identifier textureLocation, int textureIndex, int packedLight, boolean previewMode, boolean extraPlayerMode, boolean worldRenderMode, SubmitNodeCollector collector) {
            if (!GeneralConfig.safeGet(GeneralConfig.ANIMATION_DEBUG_LOG, false)) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastDebugLogMillis >= 5000L) {
                debugLogCount = 0;
                lastDebugLogMillis = now;
            }
            if (debugLogCount >= 40) {
                return;
            }
            debugLogCount++;
            YesSteveModel.LOGGER.info(
                    "[SM-MODEL] geometry-submit path={} texture={} textureIndex={} renderType={} light={} bones={} preview={} extraPlayer={} world={} collector={}",
                    path,
                    textureLocation,
                    textureIndex,
                    renderType,
                    packedLight,
                    model == null || model.getGeoModel() == null || model.getGeoModel().bakedBones == null ? -1 : model.getGeoModel().bakedBones.size(),
                    previewMode,
                    extraPlayerMode,
                    worldRenderMode,
                    collector == null ? "null" : collector.getClass().getName()
            );
        }
    }
}
