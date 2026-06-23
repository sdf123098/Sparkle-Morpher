package com.micaftic.morpher.fabric.mixin.client;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.renderer.layer.CustomPlayerElytraLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WingsLayer.class)
public abstract class WingsLayerMixin {

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V", at = @At("HEAD"), cancellable = true)
    private void ysm$suppressVanillaWings(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, HumanoidRenderState renderState, float yRot, float xRot, CallbackInfo ci) {
        if (shouldCancel(renderState)) {
            ci.cancel();
        }
    }

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/EntityRenderState;FF)V", at = @At("HEAD"), cancellable = true)
    private void ysm$suppressVanillaWingsBridge(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, EntityRenderState renderState, float yRot, float xRot, CallbackInfo ci) {
        if (shouldCancel(renderState)) {
            ci.cancel();
        }
    }

    private static boolean shouldCancel(EntityRenderState renderState) {
        if (!(renderState instanceof AvatarRenderState avatarState) || Minecraft.getInstance().level == null) {
            return false;
        }
        net.minecraft.world.entity.Entity entity = Minecraft.getInstance().level.getEntity(avatarState.id);
        if (!(entity instanceof AbstractClientPlayer player)) {
            return false;
        }
        PlayerCapability capability = PlayerCapability.get(player).orElse(null);
        return capability != null
                && capability.isModelActive()
                && capability.isModelReady()
                && CustomPlayerElytraLayer.shouldSuppressVanillaWings(capability);
    }
}
