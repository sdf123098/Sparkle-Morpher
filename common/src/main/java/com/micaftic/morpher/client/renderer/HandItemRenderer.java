package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.entity.PlayerGeoEntity;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.event.api.SpecialPlayerRenderEvent;
import com.micaftic.morpher.geckolib3.geo.LayerTypeConstants;
import com.elfmcys.yesstevemodel.geckolib3.geo.ModelRendererBridge;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;

public class HandItemRenderer {

    private PlayerGeoEntity geoModel = null;

    /**
     * 渲染第一人称自定义手模型。
     *
     * @return true = 已成功提交自定义手几何（调用方应取消原版手渲染）；false = 未渲染
     *         （模型未就绪/事件拦截等），调用方应回退原版手，避免"取消原版手但什么都没画"的空白。
     */
    public boolean renderHandItem(LocalPlayer localPlayer, ModelAssembly modelAssembly, PlayerCapability capability, HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector, int packedLight, float partialTick) {
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
}
