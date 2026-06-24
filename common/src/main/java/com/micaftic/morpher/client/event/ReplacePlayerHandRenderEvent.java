package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.renderer.RendererManager;
import com.micaftic.morpher.config.GeneralConfig;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

public class ReplacePlayerHandRenderEvent {

    private ReplacePlayerHandRenderEvent() {
    }

    public static boolean onRenderArm(Player player, HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector, int packedLight) {
        if (!YesSteveModel.isAvailable() || GeneralConfig.safeGet(GeneralConfig.DISABLE_SELF_MODEL) || GeneralConfig.safeGet(GeneralConfig.DISABLE_SELF_HANDS)) {
            return false;
        }
        if (!(player instanceof LocalPlayer localPlayer)) {
            return false;
        }
        boolean[] cancelled = {false};
        try {
            PlayerCapability.get(localPlayer).ifPresent(cap -> {
                if (!cap.isModelActive()) {
                    return;
                }
                ModelAssembly context = cap.getModelAssembly();
                if (context == null || !hasArmBone(arm, context.getAnimationBundle().getArmModel())) {
                    return;
                }
                RendererManager.getHandRenderer().renderHandItem(localPlayer, context, cap, arm, poseStack, collector, packedLight, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
                cancelled[0] = true;
            });
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("Failed to render custom hand model, falling back to vanilla", e);
            return false;
        }
        return cancelled[0];
    }

    private static boolean hasArmBone(HumanoidArm humanoidArm, GeoModel meshData) {
        if (humanoidArm == HumanoidArm.LEFT) {
            return meshData.hasCustomLeftHand;
        }
        return meshData.hasCustomRightHand;
    }
}
