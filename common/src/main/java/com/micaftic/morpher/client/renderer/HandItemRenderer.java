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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HandItemRenderer {

    private PlayerGeoEntity geoModel = null;

    /**
     * 渲染第一人称自定义手模型。
     *
     * @return true = 已成功提交自定义手几何（调用方应取消原版手渲染）；false = 未渲染
     *         （模型未就绪/事件拦截等），调用方应回退原版手，避免"取消原版手但什么都没画"的空白。
     */
    public boolean renderHandItem(LocalPlayer localPlayer, ModelAssembly modelAssembly, PlayerCapability capability, HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector, int packedLight, float partialTick) {
        if (modelAssembly.isGltf()) {
            return renderGltfHand(localPlayer, modelAssembly, arm, poseStack, collector, packedLight, partialTick);
        }
        AnimatedGeoModel model;
        if (this.geoModel == null || this.geoModel.getEntity() != localPlayer) {
            this.geoModel = new PlayerGeoEntity(localPlayer, capability);
        }
        this.geoModel.tickModel();
        ModelPreviewRenderer.setFirstPersonMode(true);
        try {
            if (this.geoModel.processAnimationImpl(partialTick, true) == null || (model = this.geoModel.getCurrentModel()) == null) {
                return false;
            }
        } finally {
            ModelPreviewRenderer.setFirstPersonMode(false);
        }
        ClientModelManager.markModelUsed(this.geoModel.getModelId());
        SpecialPlayerRenderEvent event = new SpecialPlayerRenderEvent(localPlayer, capability, capability.getModelId());
        if (SpecialPlayerRenderEvent.post(event).isFalse()) {
            return false;
        }
        Identifier Identifier = event.getTextureLocation() == null ? capability.getTextureLocation() : event.getTextureLocation();
        int textureIndex = event.getTextureLocation() == null ? capability.getTextureIndex() : 0;
        int renderPartMask = arm == HumanoidArm.LEFT ? LayerTypeConstants.TYPE_LEFT : LayerTypeConstants.TYPE_RIGHT;
        poseStack.pushPose();
        if (arm == HumanoidArm.LEFT) {
            poseStack.translate(0.25d, 1.8d, 0.0d);
        } else {
            poseStack.translate(-0.25d, 1.8d, 0.0d);
        }
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        // 第一人称手部 pass 按 vanilla 语义只收集 translucent 几何（AvatarRenderer.renderHand
        // 恒用 entityTranslucent）；对不透明贴图改用 entityCutout 会让手部几何不进入该 pass，
        // 表现为手完全不可见。恒用 translucent 与 1.21.1 分支（CustomEntityTranslucentRenderType）一致。
        RenderType renderType = RenderTypes.entityTranslucent(Identifier);
        float[] boneParams = model.getMatrixData().clone();
        float[] absPivotData = model.getAbsPivotData().clone();
        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            boolean previousFirstPersonMode = ModelPreviewRenderer.isFirstPerson();
            ModelPreviewRenderer.setFirstPersonMode(true);
            try {
                ModelRendererBridge.renderMesh(buffer, pose, model.getGeoModel(), boneParams, absPivotData, textureIndex, renderPartMask, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f, Identifier, false);
            } finally {
                ModelPreviewRenderer.setFirstPersonMode(previousFirstPersonMode);
            }
        });
        poseStack.popPose();
        return true;
    }

    private boolean renderGltfHand(LocalPlayer localPlayer, ModelAssembly assembly, HumanoidArm arm,
                                   PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                                   float partialTick) {
        GltfModel model = assembly.getGltfModel();
        if (model == null || model.scenes().isEmpty() || model.defaultScene() < 0) {
            return false;
        }
        String armToken = arm == HumanoidArm.LEFT ? "leftarm" : "rightarm";
        boolean hasArmNode = model.nodes().stream().anyMatch(node -> {
            String name = node.name();
            return name != null && name.toLowerCase(Locale.ROOT).contains(armToken) && node.meshIndex() >= 0;
        });
        if (!hasArmNode) {
            return false;
        }

        GltfSceneEvaluator evaluator = new GltfSceneEvaluator(model);
        float clock = GltfAnimationClock.fromMinecraftTicks(localPlayer.tickCount, partialTick);
        GltfAnimationController controller = new GltfAnimationController(model);
        controller.selectForMotion((float) localPlayer.getDeltaMovement().horizontalDistance(), localPlayer.onGround(),
                localPlayer.isCrouching(), false, clock);
        GltfSceneEvaluator.Pose pose = controller.evaluate(evaluator, model.defaultScene(), clock);
        List<GltfModel.Material> passes = new ArrayList<>();
        boolean includeDefaultMaterial = false;
        for (GltfModel.Node node : model.nodes()) {
            if (node.meshIndex() < 0) continue;
            for (GltfModel.Primitive primitive : model.meshes().get(node.meshIndex()).primitives()) {
                if (primitive.materialIndex() < 0) {
                    includeDefaultMaterial = true;
                } else {
                    GltfModel.Material material = model.materials().get(primitive.materialIndex());
                    if (!passes.contains(material)) passes.add(material);
                }
            }
        }
        if (includeDefaultMaterial) passes.add(null);
        if (passes.isEmpty()) passes.add(null);

        poseStack.pushPose();
        try {
            if (arm == HumanoidArm.LEFT) {
                poseStack.translate(0.25d, 1.8d, 0.0d);
            } else {
                poseStack.translate(-0.25d, 1.8d, 0.0d);
            }
            float scale = model.recommendedMinecraftScale();
            poseStack.scale(-scale, -scale, scale);
            for (GltfModel.Material pass : passes) {
                GltfMaterialResolver.ResolvedMaterial<Identifier> resolved = GltfMaterialResolver.resolve(
                        pass, ClientModelManager.getDefaultTexture(), null, textureIndex -> {
                            var texture = assembly.getGltfTexture(pass == null ? -1 : pass.baseColorTextureIndex());
                            if (texture == null) return null;
                            IResourceLocatable locatable = UploadManager.getOrCreateLocatable(texture, true);
                            return locatable.getResourceLocationOrNull();
                        });
                Identifier texture = resolved.texture();
                if (texture == null) continue;
                RenderType renderType = GltfRenderTypes.get(texture, resolved.alphaMode(), resolved.doubleSided());
                collector.submitCustomGeometry(poseStack, renderType, (capturedPose, buffer) ->
                        GltfVertexConsumerRenderer.render(model, evaluator, pose, capturedPose,
                                material -> buffer, packedLight, OverlayTexture.NO_OVERLAY,
                                1.0f, 1.0f, 1.0f, 1.0f,
                                node -> {
                                    String name = node.name();
                                    return name != null && name.toLowerCase(Locale.ROOT).contains(armToken);
                                }, material -> material == pass));
            }
            return true;
        } finally {
            poseStack.popPose();
        }
    }
}
