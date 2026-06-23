package com.micaftic.morpher.fabric.mixin.client;

import com.micaftic.morpher.client.event.ReplacePlayerHandRenderEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class PlayerRendererHandMixin {

    // MC 26.x Fabric: renderRightHand has 5 params, no AbstractClientPlayer.
    // Player is available from Minecraft.getInstance().player for first-person hand rendering.
    @Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true)
    private void ysm$onRenderRightHand(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, Identifier id, boolean flag, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            if (ReplacePlayerHandRenderEvent.onRenderArm(player, HumanoidArm.RIGHT, poseStack, collector, packedLight)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true)
    private void ysm$onRenderLeftHand(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, Identifier id, boolean flag, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            if (ReplacePlayerHandRenderEvent.onRenderArm(player, HumanoidArm.LEFT, poseStack, collector, packedLight)) {
                ci.cancel();
            }
        }
    }
}
