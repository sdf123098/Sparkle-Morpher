package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.diagnostics.FirstPersonRenderDiagnostics;
import com.micaftic.morpher.client.diagnostics.FirstPersonRenderDiagnostics.Outcome;
import com.micaftic.morpher.client.entity.PlayerGeoEntity;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.renderer.gltf.GltfMaterialResolver;
import com.micaftic.morpher.client.renderer.gltf.GltfPlayerActionMapper;
import com.micaftic.morpher.client.renderer.gltf.GltfRenderTypes;
import com.micaftic.morpher.client.renderer.gltf.GltfVertexConsumerRenderer;
import com.micaftic.morpher.client.upload.IResourceLocatable;
import com.micaftic.morpher.client.upload.UploadManager;
import com.micaftic.morpher.event.api.SpecialPlayerRenderEvent;
import com.micaftic.morpher.geckolib3.geo.LayerTypeConstants;
import com.elfmcys.yesstevemodel.geckolib3.geo.ModelRendererBridge;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.micaftic.morpher.resource.gltf.GltfAnimationClock;
import com.micaftic.morpher.resource.gltf.GltfAnimationController;
import com.micaftic.morpher.resource.gltf.GltfModel;
import com.micaftic.morpher.resource.gltf.GltfSceneEvaluator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;

import java.util.Locale;
import java.util.function.Function;

public class HandItemRenderer {

    private PlayerGeoEntity geoModel = null;

    /**
     * 渲染第一人称自定义手模型。
     *
     * @return true = 已成功提交自定义手几何（调用方应取消原版手渲染）；false = 未渲染
     *         （模型未就绪/事件拦截等），调用方应回退原版手，避免"取消原版手但什么都没画"的空白。
     */
    public boolean renderHandItem(LocalPlayer localPlayer, ModelAssembly modelAssembly, PlayerCapability capability, HumanoidArm arm, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float partialTick) {
        if (modelAssembly.isGltf()) {
            return renderGltfHand(localPlayer, modelAssembly, capability.getModelId(), arm, poseStack, bufferSource, packedLight, partialTick);
        }
        AnimatedGeoModel model;
        if (this.geoModel == null || this.geoModel.getEntity() != localPlayer) {
            this.geoModel = new PlayerGeoEntity(localPlayer, capability);
        }
        this.geoModel.tickModel();
        if (this.geoModel.processAnimation(partialTick) == null || (model = this.geoModel.getCurrentModel()) == null) {
            FirstPersonRenderDiagnostics.report(arm, Outcome.SKIPPED_ANIMATION_NOT_READY, capability.getModelId(), "geckolib", null);
            return false;
        }
        ClientModelManager.markModelUsed(this.geoModel.getModelId());
        SpecialPlayerRenderEvent event = new SpecialPlayerRenderEvent(localPlayer, capability, capability.getModelId());
        if (SpecialPlayerRenderEvent.post(event)) {
            FirstPersonRenderDiagnostics.report(arm, Outcome.SKIPPED_VETOED_BY_EVENT, capability.getModelId(), "geckolib", null);
            return false;
        }
        ResourceLocation resourceLocation = event.getTextureLocation() == null ? capability.getTextureLocation() : event.getTextureLocation();
        int textureIndex = event.getTextureLocation() == null ? capability.getTextureIndex() : 0;
        VertexConsumer buffer = bufferSource.getBuffer(CustomEntityTranslucentRenderType.get(resourceLocation));
        int renderPartMask = arm == HumanoidArm.LEFT ? LayerTypeConstants.TYPE_LEFT : LayerTypeConstants.TYPE_RIGHT;
        poseStack.pushPose();
        if (arm == HumanoidArm.LEFT) {
            poseStack.translate(0.25d, 1.8d, 0.0d);
        } else {
            poseStack.translate(-0.25d, 1.8d, 0.0d);
        }
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        ModelRendererBridge.renderMesh(buffer, poseStack.last(), model.getGeoModel(), model.getMatrixData(), model.getAbsPivotData(), textureIndex, renderPartMask, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f, resourceLocation);
        poseStack.popPose();
        return true;
    }

    private boolean renderGltfHand(LocalPlayer localPlayer, ModelAssembly assembly, String modelId, HumanoidArm arm,
                                   PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                   float partialTick) {
        GltfModel model = assembly.getGltfModel();
        if (model == null || model.scenes().isEmpty() || model.defaultScene() < 0) {
            FirstPersonRenderDiagnostics.report(arm, Outcome.SKIPPED_GLTF_INVALID, modelId, "gltf", null);
            return false;
        }
        String armToken = arm == HumanoidArm.LEFT ? "leftarm" : "rightarm";
        boolean hasArmNode = model.nodes().stream().anyMatch(node -> node.name() != null
                && node.name().toLowerCase(Locale.ROOT).contains(armToken) && node.meshIndex() >= 0);
        if (!hasArmNode) {
            FirstPersonRenderDiagnostics.report(arm, Outcome.SKIPPED_GLTF_NO_ARM_NODE, modelId, "gltf", false);
            return false;
        }
        GltfSceneEvaluator evaluator = new GltfSceneEvaluator(model);
        float clock = GltfAnimationClock.fromMinecraftTicks(localPlayer.tickCount, partialTick);
        GltfAnimationController controller = new GltfAnimationController(model);
        controller.selectState(GltfPlayerActionMapper.resolveForMotion(localPlayer), clock);
        GltfSceneEvaluator.Pose pose = controller.evaluate(evaluator, model.defaultScene(), clock);
        Function<GltfModel.Material, VertexConsumer> consumerFactory = material -> {
            GltfMaterialResolver.ResolvedMaterial<ResourceLocation> resolved = GltfMaterialResolver.resolve(
                    material, ClientModelManager.getDefaultTexture(), null, textureIndex -> {
                        var texture = assembly.getGltfTexture(material == null ? -1 : material.baseColorTextureIndex());
                        if (texture == null) return null;
                        IResourceLocatable locatable = UploadManager.getOrCreateLocatable(texture, true);
                        return locatable.getResourceLocationOrNull();
                    });
            return bufferSource.getBuffer(GltfRenderTypes.get(resolved.texture(), resolved.alphaMode(), resolved.doubleSided()));
        };
        poseStack.pushPose();
        try {
            poseStack.translate(arm == HumanoidArm.LEFT ? 0.25d : -0.25d, 1.8d, 0.0d);
            float scale = model.recommendedMinecraftScale();
            poseStack.scale(-scale, -scale, scale);
            GltfVertexConsumerRenderer.render(model, evaluator, pose, poseStack, consumerFactory,
                    packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f,
                    node -> node.name() != null && node.name().toLowerCase(Locale.ROOT).contains(armToken));
        } finally {
            poseStack.popPose();
        }
        return true;
    }
}
