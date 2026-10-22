package com.micaftic.morpher.neoforge.mixin.compat.punchy;

import com.micaftic.morpher.client.compat.punchy.PunchyArmBridge;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * [FP-COMPAT] Version-gated bridge into Punchy!'s first-person arm pass (Punchy! 2.8g).
 *
 * <p>The mixin itself is only applied by {@code SparkleNeoForgeMixinPlugin} when a supported
 * Punchy! build is installed. The injection shape was derived from Punchy!'s own bytecode:
 * {@code renderArm(PlayerModel, AbstractClientPlayer, HumanoidArm, PoseStack, MultiBufferSource, int, ResourceLocation, boolean, float)}
 * draws {@code model.rightArm/leftArm} after a slim-arm X offset and Punchy!'s arm mesh tuning.
 * Only the slim offset and the model part transform are reproduced here; Punchy!'s mesh tuning
 * and freeze shake are left out, so SPM's arm follows the animated arm pose without borrowing
 * Punchy! code.
 */
@Mixin(targets = "punchy.client.render.PunchyArmRenderer", remap = false)
public abstract class PunchyArmRendererMixin {

    @Inject(method = "renderArm", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sparkle$replaceArm(PlayerModel model, AbstractClientPlayer player, HumanoidArm arm,
                                           PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                           ResourceLocation texture, boolean slim, float partialTick, CallbackInfo ci) {
        PunchyArmBridge.beginArm(player, arm, bufferSource);
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        boolean rendered;
        poseStack.pushPose();
        try {
            if (slim) {
                poseStack.translate(arm == HumanoidArm.RIGHT ? 0.03125d : -0.03125d, 0.0d, 0.0d);
            }
            rendered = PunchyArmBridge.tryReplaceArm(part, poseStack, packedLight);
        } finally {
            poseStack.popPose();
        }
        if (rendered) {
            ci.cancel();
        }
    }

    @Inject(method = "renderArmArmor", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sparkle$suppressArmor(PlayerModel model, AbstractClientPlayer player, HumanoidArm arm,
                                              PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                              boolean slim, float partialTick, CallbackInfo ci) {
        if (PunchyArmBridge.consumeArmorSuppression(arm)) {
            ci.cancel();
        }
    }
}
