package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.renderer.RendererManager;
import com.micaftic.morpher.client.render.PlayerRenderPolicy;
import com.micaftic.morpher.core.config.ConfigPolicies;
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
        PlayerCapability cap = null;
        try {
            cap = PlayerCapability.get(entity).orElse(null);
            boolean firstPersonSuppressionSatisfied = cap != null
                    && (!CameraUtil.isFirstPerson(cap)
                        || FirstPersonCompat.isFirstPersonActive()
                        || RealCameraCompat.isActive()
                        || ConfigPolicies.render().disableExternalFirstPersonAnimation()
                        || !PlayerAnimatorCompat.isPlayerAnimated(localPlayer));
            PlayerRenderPolicy.Decision decision = PlayerRenderPolicy.decide(new PlayerRenderPolicy.GateInputs(
                    true,
                    entity.equals(localPlayer),
                    ConfigPolicies.render().disableSelfModel(),
                    ConfigPolicies.render().disableOtherModel(),
                    entity.isSpectator(),
                    cap != null && cap.isModelActive(),
                    firstPersonSuppressionSatisfied
            ));
            if (decision == PlayerRenderPolicy.Decision.RENDER_CUSTOM) {
                RendererManager.getPlayerRenderer().render(cap, entityYaw, partialTick, poseStack, bufferSource, collector, packedLight);
                return true;
            }
            return false;
        } catch (Exception e) {
            boolean suppressVanillaFallback = cap != null && cap.isModelActive() && cap.hasRenderableModel();
            YesSteveModel.LOGGER.warn("Failed to render custom player model; suppressing vanilla fallback={}", suppressVanillaFallback, e);
            return suppressVanillaFallback;
        }
    }
}
