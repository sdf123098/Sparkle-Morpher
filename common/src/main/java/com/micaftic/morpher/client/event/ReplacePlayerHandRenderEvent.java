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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

public class ReplacePlayerHandRenderEvent {

    private ReplacePlayerHandRenderEvent() {
    }

    public static boolean onRenderArm(Player player, HumanoidArm arm, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (!YesSteveModel.isAvailable() || GeneralConfig.DISABLE_SELF_MODEL.get() || GeneralConfig.DISABLE_SELF_HANDS.get()) {
            return false;
        }
        if (!(player instanceof LocalPlayer localPlayer)) {
            return false;
        }
        boolean[] cancelled = {false};
        PlayerCapability.get(localPlayer).ifPresent(cap -> {
            if (!cap.isModelActive()) {
                return;
            }
            ModelAssembly context = cap.getModelAssembly();
            if (context == null || !hasArmBone(arm, context.getAnimationBundle().getArmModel())) {
                return;
            }
            RendererManager.getHandRenderer().renderHandItem(localPlayer, context, cap, arm, poseStack, bufferSource, packedLight, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
            cancelled[0] = true;
        });
        return cancelled[0];
    }

    private static boolean hasArmBone(HumanoidArm humanoidArm, GeoModel meshData) {
        if (humanoidArm == HumanoidArm.LEFT) {
            return meshData.hasCustomLeftHand;
        }
        return meshData.hasCustomRightHand;
    }
}
