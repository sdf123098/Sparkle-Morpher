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
import com.micaftic.morpher.client.renderer.MultiBufferSource;
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
            if (cap != null) cap.tickModel();
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
                    cap != null && cap.isModelActive() && cap.hasRenderableModel(),
                    firstPersonSuppressionSatisfied
            ));
            if (decision == PlayerRenderPolicy.Decision.RENDER_CUSTOM) {
                if (com.micaftic.morpher.client.peer.ClientPeerRuntime.ownsAppearance(entity.getUUID())
                        && (!cap.isModelReady() || !com.micaftic.morpher.peer.PeerFrameWork.PEER.admitModel(cap.getModelId()))) {
                    com.micaftic.morpher.client.peer.ClientPeerRuntime.renderState(entity, cap.getModelId(), com.micaftic.morpher.peer.PeerObservation.State.WAITING_RENDER);
                    return true;
                }
                RendererManager.getPlayerRenderer().render(cap, entityYaw, partialTick, poseStack, bufferSource, collector, packedLight);
                com.micaftic.morpher.client.peer.ClientPeerRuntime.renderState(entity, cap.getModelId(), com.micaftic.morpher.peer.PeerObservation.State.RENDER_SUBMITTED);
                return true;
            }
            if(cap != null) com.micaftic.morpher.client.peer.ClientPeerRuntime.renderState(entity, cap.getModelId(),
                    ConfigPolicies.render().disableOtherModel() || entity.isSpectator() ? com.micaftic.morpher.peer.PeerObservation.State.HIDDEN
                            : com.micaftic.morpher.peer.PeerObservation.State.WAITING_RENDER);
            return false;
        } catch (Exception e) {
            if(cap != null) com.micaftic.morpher.client.peer.ClientPeerRuntime.renderState(entity, cap.getModelId(), com.micaftic.morpher.peer.PeerObservation.State.FAILED);
            boolean suppressVanillaFallback = cap != null && cap.isModelActive() && cap.hasRenderableModel();
            YesSteveModel.LOGGER.warn("Failed to render custom player model; suppressing vanilla fallback={}", suppressVanillaFallback, e);
            return suppressVanillaFallback;
        }
    }
}
