package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.event.ReplacePlayerRenderEvent;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class PlayerRendererMixin {

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void ysm$onSubmit(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState avatarState) || Minecraft.getInstance().level == null) {
            return;
        }
        if (!(Minecraft.getInstance().level.getEntity(avatarState.id) instanceof Player player)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        float partialTick = ((MinecraftAccessor) minecraft).ysm$getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int packedLight = ((MinecraftAccessor) minecraft).ysm$getEntityRenderDispatcher().getPackedLightCoords(player, partialTick);
        boolean preview = ModelPreviewRenderer.isPreview();
        float yaw = preview ? state.bodyRot : state.yRot;

        float oldBodyRot = player.yBodyRot;
        float oldBodyRotO = player.yBodyRotO;
        float oldYRot = player.getYRot();
        float oldYRotO = player.yRotO;
        float oldXRot = player.getXRot();
        float oldXRotO = player.xRotO;
        float oldHeadRot = player.yHeadRot;
        float oldHeadRotO = player.yHeadRotO;
        if (preview) {
            float bodyRot = state.bodyRot;
            float headRot = bodyRot + state.yRot;
            player.yBodyRot = bodyRot;
            player.yBodyRotO = bodyRot;
            player.setYRot(headRot);
            player.yRotO = headRot;
            player.setXRot(state.xRot);
            player.xRotO = state.xRot;
            player.yHeadRot = headRot;
            player.yHeadRotO = headRot;
        }

        PlayerCapability capability = PlayerCapability.get(player).orElse(null);
        if (capability != null) {
            capability.beginRenderState(avatarState);
        }
        try {
            MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
            if (ReplacePlayerRenderEvent.onRenderPlayerPre(player, yaw, partialTick, poseStack, bufferSource, collector, packedLight)) {
                bufferSource.endBatch();
                ci.cancel();
            }
        } finally {
            if (capability != null) {
                capability.endRenderState();
            }
            if (preview) {
                player.yBodyRot = oldBodyRot;
                player.yBodyRotO = oldBodyRotO;
                player.setYRot(oldYRot);
                player.yRotO = oldYRotO;
                player.setXRot(oldXRot);
                player.xRotO = oldXRotO;
                player.yHeadRot = oldHeadRot;
                player.yHeadRotO = oldHeadRotO;
            }
        }
    }
}
