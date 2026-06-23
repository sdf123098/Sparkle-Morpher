package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.entity.PlayerGeoEntity;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.event.api.SpecialPlayerRenderEvent;
import com.micaftic.morpher.geckolib3.geo.LayerTypeConstants;
import com.micaftic.morpher.geckolib3.geo.NativeModelRenderer;
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

    public void renderHandItem(LocalPlayer localPlayer, ModelAssembly modelAssembly, PlayerCapability capability, HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector, int packedLight, float partialTick) {
        AnimatedGeoModel model;
        if (this.geoModel == null || this.geoModel.getEntity() != localPlayer) {
            this.geoModel = new PlayerGeoEntity(localPlayer, capability);
        }
        this.geoModel.tickModel();
        ModelPreviewRenderer.setFirstPersonMode(true);
        try {
            if (this.geoModel.processAnimationImpl(partialTick, true) == null || (model = this.geoModel.getCurrentModel()) == null) {
                return;
            }
        } finally {
            ModelPreviewRenderer.setFirstPersonMode(false);
        }
        SpecialPlayerRenderEvent event = new SpecialPlayerRenderEvent(localPlayer, capability, capability.getModelId());
        if (SpecialPlayerRenderEvent.post(event).isFalse()) {
            return;
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
        RenderType renderType = model.getGeoModel().isTranslucentTexture(textureIndex)
                ? RenderTypes.entityTranslucent(Identifier)
                : RenderTypes.entityCutout(Identifier);
        float[] boneParams = model.getMatrixData().clone();
        float[] absPivotData = model.getAbsPivotData().clone();
        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) ->
                NativeModelRenderer.renderMesh(buffer, pose, model.getGeoModel(), boneParams, absPivotData, textureIndex, renderPartMask, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f, Identifier, false));
        poseStack.popPose();
    }
}
