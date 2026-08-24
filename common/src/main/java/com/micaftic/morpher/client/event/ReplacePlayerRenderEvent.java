package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.renderer.RendererManager;
import com.micaftic.morpher.core.config.ConfigPolicies;
import com.micaftic.morpher.util.CameraUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import com.micaftic.morpher.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.player.Player;
import com.micaftic.morpher.core.compat.firstperson.FirstPersonCompat;
import com.micaftic.morpher.core.compat.playeranimator.PlayerAnimatorCompat;
import com.micaftic.morpher.core.compat.realcamera.RealCameraCompat;

public class ReplacePlayerRenderEvent {
    private static int debugLogCount;
    private static long lastDebugLogMillis;

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
        if (entity.equals(localPlayer) && ConfigPolicies.render().disableSelfModel()) {
            return false;
        }
        if ((!entity.equals(localPlayer) && ConfigPolicies.render().disableOtherModel()) || entity.isSpectator()) {
            return false;
        }
        PlayerCapability cap = null;
        try {
            cap = PlayerCapability.get(entity).orElse(null);
            if (cap != null && cap.isModelActive()) {
                if (!CameraUtil.isFirstPerson(cap)
                        || FirstPersonCompat.isFirstPersonActive()
                        || RealCameraCompat.isActive()
                        || ConfigPolicies.render().disableExternalFirstPersonAnimation()
                        || !PlayerAnimatorCompat.isPlayerAnimated(localPlayer)) {
                    // Reuse the capability resolved for the render-pre check.
                    RendererManager.getPlayerRenderer().render(cap, entityYaw, partialTick, poseStack, bufferSource, collector, packedLight);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            boolean suppressVanillaFallback = cap != null && cap.isModelActive() && cap.hasRenderableModel();
            YesSteveModel.LOGGER.warn("Failed to render custom player model; suppressing vanilla fallback={}", suppressVanillaFallback, e);
            return suppressVanillaFallback;
        }
    }

    private static void logModelRenderState(String reason, Player player, PlayerCapability cap, MultiBufferSource bufferSource, SubmitNodeCollector collector, String detail) {
        if (!ConfigPolicies.diagnostics().animationDebugLog()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (debugLogCount >= 80 && now - lastDebugLogMillis < 5000L) {
            return;
        }
        if (now - lastDebugLogMillis >= 5000L) {
            debugLogCount = 0;
            lastDebugLogMillis = now;
        }
        debugLogCount++;
        YesSteveModel.LOGGER.info(
                "[SM-MODEL] player-render reason={} player={} entityId={} modelId={} active={} renderable={} ready={} buffer={} collector={} detail={}",
                reason,
                player.getName().getString(),
                player.getId(),
                cap == null ? "<none>" : cap.getModelId(),
                cap != null && cap.isModelActive(),
                cap != null && cap.hasRenderableModel(),
                cap != null && cap.isModelReady(),
                bufferSource == null ? "null" : bufferSource.getClass().getName(),
                collector == null ? "null" : collector.getClass().getName(),
                detail == null ? "" : detail
        );
    }
}
