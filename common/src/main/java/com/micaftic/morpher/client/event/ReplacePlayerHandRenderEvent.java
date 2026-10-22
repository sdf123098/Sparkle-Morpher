package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.diagnostics.FirstPersonRenderDiagnostics;
import com.micaftic.morpher.client.diagnostics.FirstPersonRenderDiagnostics.Outcome;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.renderer.RendererManager;
import com.micaftic.morpher.core.config.ConfigPolicies;
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
        if (!YesSteveModel.isAvailable()) {
            FirstPersonRenderDiagnostics.report(arm, Outcome.SKIPPED_MOD_UNAVAILABLE, null, null, null);
            return false;
        }
        if (ConfigPolicies.render().disableSelfModel()) {
            FirstPersonRenderDiagnostics.report(arm, Outcome.SKIPPED_SELF_MODEL_DISABLED, null, null, null);
            return false;
        }
        if (ConfigPolicies.render().disableSelfHands()) {
            FirstPersonRenderDiagnostics.report(arm, Outcome.SKIPPED_SELF_HANDS_DISABLED, null, null, null);
            return false;
        }
        if (!(player instanceof LocalPlayer localPlayer)) {
            FirstPersonRenderDiagnostics.report(arm, Outcome.SKIPPED_NOT_LOCAL_PLAYER, null, null, null);
            return false;
        }
        PlayerCapability capability = PlayerCapability.get(localPlayer).orElse(null);
        if (capability == null) {
            FirstPersonRenderDiagnostics.report(arm, Outcome.SKIPPED_NO_CAPABILITY, null, null, null);
            return false;
        }
        if (!capability.isModelActive()) {
            FirstPersonRenderDiagnostics.report(arm, Outcome.SKIPPED_MODEL_INACTIVE, capability.getModelId(), null, null);
            return false;
        }
        ModelAssembly assembly = capability.getModelAssembly();
        if (assembly == null) {
            FirstPersonRenderDiagnostics.report(arm, Outcome.SKIPPED_NO_ASSEMBLY, capability.getModelId(), null, null);
            return false;
        }
        if (assembly.isGltf()) {
            boolean rendered = renderHand(localPlayer, assembly, capability, arm, poseStack, bufferSource, packedLight);
            if (rendered) {
                FirstPersonRenderDiagnostics.report(arm, Outcome.REPLACED_GLTF, capability.getModelId(), "gltf", null);
            }
            return rendered;
        }
        if (assembly.getAnimationBundle() == null) {
            FirstPersonRenderDiagnostics.report(arm, Outcome.SKIPPED_NO_ANIMATION_BUNDLE, capability.getModelId(), "geckolib", null);
            return false;
        }
        boolean armBoneExists = hasArmBone(arm, assembly.getAnimationBundle().getArmModel());
        if (!armBoneExists) {
            FirstPersonRenderDiagnostics.report(arm, Outcome.SKIPPED_NO_ARM_BONE, capability.getModelId(), "geckolib", false);
            return false;
        }
        boolean rendered = renderHand(localPlayer, assembly, capability, arm, poseStack, bufferSource, packedLight);
        if (rendered) {
            FirstPersonRenderDiagnostics.report(arm, Outcome.REPLACED, capability.getModelId(), "geckolib", true);
        }
        return rendered;
    }

    /**
     * Only cancels the vanilla arm when the custom arm was actually submitted; a failure here must
     * leave the vanilla arm visible rather than blanking the hand region.
     */
    private static boolean renderHand(LocalPlayer localPlayer, ModelAssembly assembly, PlayerCapability capability,
                                      HumanoidArm arm, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        try {
            return RendererManager.getHandRenderer().renderHandItem(localPlayer, assembly, capability, arm,
                    poseStack, bufferSource, packedLight,
                    Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("Failed to render custom hand model for arm {}, falling back to vanilla", arm, e);
            FirstPersonRenderDiagnostics.report(arm, Outcome.FAILED_EXCEPTION, capability.getModelId(),
                    assembly.isGltf() ? "gltf" : "geckolib", null);
            return false;
        }
    }

    private static boolean hasArmBone(HumanoidArm humanoidArm, GeoModel meshData) {
        if (humanoidArm == HumanoidArm.LEFT) {
            return meshData.hasCustomLeftHand;
        }
        return meshData.hasCustomRightHand;
    }
}
