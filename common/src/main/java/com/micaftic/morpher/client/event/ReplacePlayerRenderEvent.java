package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.renderer.RendererManager;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.util.CameraUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.player.Player;
import com.micaftic.morpher.core.compat.firstperson.FirstPersonCompat;
import com.micaftic.morpher.core.compat.playeranimator.PlayerAnimatorCompat;
import com.micaftic.morpher.core.compat.realcamera.RealCameraCompat;

public class ReplacePlayerRenderEvent {

    private ReplacePlayerRenderEvent() {
    }

    public static boolean onRenderPlayerPre(Player entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        return onRenderPlayerPre(entity, entity.getYRot(), partialTick, poseStack, bufferSource, null, packedLight);
    }

    public static boolean onRenderPlayerPre(Player entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, SubmitNodeCollector collector, int packedLight) {
        if (!YesSteveModel.isAvailable()) {
            return false;
        }
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (entity.equals(localPlayer) && GeneralConfig.safeGet(GeneralConfig.DISABLE_SELF_MODEL)) {
            return false;
        }
        if ((!entity.equals(localPlayer) && GeneralConfig.safeGet(GeneralConfig.DISABLE_OTHER_MODEL)) || entity.isSpectator()) {
            return false;
        }
        try {
            PlayerCapability cap = PlayerCapability.get(entity).orElse(null);
            if (cap != null && cap.isModelActive()) {
                if (!CameraUtil.isFirstPerson(cap)
                        || FirstPersonCompat.isFirstPersonActive()
                        || RealCameraCompat.isActive()
                        || GeneralConfig.safeGet(GeneralConfig.DISABLE_EXTERNAL_FP_ANIM)
                        || !PlayerAnimatorCompat.isPlayerAnimated(localPlayer)) {
                    // Reuse the capability resolved for the render-pre check.
                    RendererManager.getPlayerRenderer().render(cap, entityYaw, partialTick, poseStack, bufferSource, collector, packedLight);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("Failed to render custom player model, falling back to vanilla", e);
            return false;
        }
    }
}
