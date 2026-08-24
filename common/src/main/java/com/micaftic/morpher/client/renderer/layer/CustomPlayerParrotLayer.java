package com.micaftic.morpher.client.renderer.layer;

import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.geckolib3.geo.GeoLayerRenderer;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.micaftic.morpher.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ParrotRenderer;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.player.Player;
import com.mojang.math.Axis;

public class CustomPlayerParrotLayer extends GeoLayerRenderer<CustomPlayerEntity> {

    private final ParrotModel parrotModel;

    public CustomPlayerParrotLayer(EntityRendererProvider.Context context) {
        this.parrotModel = new ParrotModel(context.bakeLayer(ModelLayers.PARROT));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLightIn, CustomPlayerEntity entityLivingBaseIn, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        Player player = entityLivingBaseIn.getEntity();
        AnimatedGeoModel model = entityLivingBaseIn.getCurrentModel();
        if (model == null) {
            return;
        }
        if (!model.leftShoulderBones().isEmpty()) {
            renderParrot(poseStack, bufferSource, model, packedLightIn, player, limbSwing, limbSwingAmount, netHeadYaw, headPitch, true);
        }
        if (!model.rightShoulderBones().isEmpty()) {
            renderParrot(poseStack, bufferSource, model, packedLightIn, player, limbSwing, limbSwingAmount, netHeadYaw, headPitch, false);
        }
    }

    private void renderParrot(PoseStack poseStack, MultiBufferSource bufferSource, AnimatedGeoModel model, int packedLightIn, Player player, float limbSwing, float limbSwingAmount, float netHeadYaw, float headPitch, boolean isLeftShoulder) {
        Parrot.Variant variant = (isLeftShoulder ? player.getShoulderParrotLeft() : player.getShoulderParrotRight()).orElse(null);
        if (variant == null) {
            return;
        }
        ParrotRenderState state = new ParrotRenderState();
        state.variant = variant;
        state.pose = ParrotModel.Pose.ON_SHOULDER;
        state.flapAngle = player.tickCount + limbSwing;
        this.parrotModel.setupAnim(state);
        poseStack.pushPose();
        applyParrotTransform(poseStack, model, isLeftShoulder);
        poseStack.translate(0.0d, 1.5d, 0.0d);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
        this.parrotModel.renderToBuffer(poseStack, bufferSource.getBuffer(RenderTypes.entityCutout(ParrotRenderer.getVariantTexture(variant))), packedLightIn, OverlayTexture.NO_OVERLAY, -1);
        poseStack.popPose();
    }

    public void applyParrotTransform(PoseStack poseStack, AnimatedGeoModel model, boolean isLeftShoulder) {
        if (isLeftShoulder) {
            RenderUtils.prepMatrixForLocator(poseStack, model.leftShoulderBones());
        } else {
            RenderUtils.prepMatrixForLocator(poseStack, model.rightShoulderBones());
        }
    }
}
