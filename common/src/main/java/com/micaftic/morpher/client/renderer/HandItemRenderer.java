package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.entity.PlayerGeoEntity;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.renderer.gltf.GltfMaterialResolver;
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

    public void renderHandItem(LocalPlayer localPlayer, ModelAssembly modelAssembly, PlayerCapability capability, HumanoidArm arm, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float partialTick) {
        if (modelAssembly.isGltf()) {
            renderGltfHand(localPlayer, modelAssembly, arm, poseStack, bufferSource, packedLight, partialTick);
            return;
        }
        AnimatedGeoModel model;
        if (this.geoModel == null || this.geoModel.getEntity() != localPlayer) {
            this.geoModel = new PlayerGeoEntity(localPlayer, capability);
        }
        this.geoModel.tickModel();
        if (this.geoModel.processAnimation(partialTick) == null || (model = this.geoModel.getCurrentModel()) == null) {
            return;
        }
        ClientModelManager.markModelUsed(this.geoModel.getModelId());
        SpecialPlayerRenderEvent event = new SpecialPlayerRenderEvent(localPlayer, capability, capability.getModelId());
        if (SpecialPlayerRenderEvent.post(event)) {
            return;
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
    }

    private void renderGltfHand(LocalPlayer localPlayer, ModelAssembly assembly, HumanoidArm arm,
                                PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                float partialTick) {
        GltfModel model = assembly.getGltfModel();
        if (model == null || model.scenes().isEmpty() || model.defaultScene() < 0) return;
        String armToken = arm == HumanoidArm.LEFT ? "leftarm" : "rightarm";
        boolean hasArmNode = model.nodes().stream().anyMatch(node -> node.name() != null
                && node.name().toLowerCase(Locale.ROOT).contains(armToken) && node.meshIndex() >= 0);
        if (!hasArmNode) return;
        GltfSceneEvaluator evaluator = new GltfSceneEvaluator(model);
        float clock = GltfAnimationClock.fromMinecraftTicks(localPlayer.tickCount, partialTick);
        GltfAnimationController controller = new GltfAnimationController(model);
        controller.selectForMotion((float) localPlayer.getDeltaMovement().horizontalDistance(), localPlayer.onGround(),
                localPlayer.isCrouching(), false, clock);
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
    }
}
