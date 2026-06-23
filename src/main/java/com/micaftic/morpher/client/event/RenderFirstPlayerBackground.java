package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.renderer.CustomEntityTranslucentRenderType;
import com.micaftic.morpher.client.renderer.CustomPlayerRenderer;
import com.micaftic.morpher.client.renderer.RendererManager;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.event.api.SpecialPlayerRenderEvent;
import com.micaftic.morpher.geckolib3.geo.NativeModelRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class RenderFirstPlayerBackground {
    // 因为RenderHandEvent可有几率会渲染多次，所以为了避免多次渲染，这样设计
    private static boolean currentFrameRendered = false;

    private RenderFirstPlayerBackground() {
    }

    public static void resetFrame() {
        currentFrameRendered = false;
    }

    public static void onRenderHand(PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight, float partialTick) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        if (GeneralConfig.DISABLE_SELF_MODEL.get()) {
            return;
        }
        if (GeneralConfig.DISABLE_SELF_HANDS.get()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || currentFrameRendered) {
            return;
        }
        currentFrameRendered = true;
        PlayerCapability.get(player).ifPresent(cap -> {
            if (!cap.isModelActive()) {
                return;
            }
            String modelId = cap.getModelId();
            ModelAssembly modelAssembly = cap.getModelAssembly();
            if (modelAssembly == null || !modelAssembly.getAnimationBundle().getArmModel().hasCustomLimbs) {
                return;
            }
            CustomPlayerRenderer instance = RendererManager.getPlayerRenderer();
            boolean result = SpecialPlayerRenderEvent.post(new SpecialPlayerRenderEvent(player, cap, modelId));
            if (!result) {
                return;
            }
            ResourceLocation resourceLocationB_ = cap.getTextureLocation();
            int textureIndex = cap.getTextureIndex();
            VertexConsumer buffer = multiBufferSource.getBuffer(CustomEntityTranslucentRenderType.get(resourceLocationB_));
            if (instance != null) {
                poseStack.pushPose();
                if (Minecraft.getInstance().options.bobView().get()) {
                    applyHandTransform(poseStack, partialTick, player);
                }
                poseStack.translate(0.0d, -1.5d, 0.0d);
                NativeModelRenderer.renderMesh(buffer, poseStack.last(), modelAssembly.getAnimationBundle().getArmModel(), modelAssembly.getAnimationBundle().getArmModel().getBoneTransformData(), null, textureIndex, 3, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f, resourceLocationB_);
                poseStack.popPose();
            }
        });
    }

    private static void applyHandTransform(PoseStack poseStack, float partialTick, Player player) {
        float walkPhase = -(player.walkDist + ((player.walkDist - player.walkDistO) * partialTick));
        float fLerp = Mth.lerp(partialTick, player.oBob, player.bob);
        poseStack.translate((-Mth.sin(walkPhase * 3.1415927f)) * fLerp * 0.5f, Math.abs(Mth.cos(walkPhase * 3.1415927f) * fLerp), 0.0d);
        poseStack.mulPose(Axis.ZN.rotationDegrees(Mth.sin(walkPhase * 3.1415927f) * fLerp * 3.0f));
        poseStack.mulPose(Axis.XN.rotationDegrees(Math.abs(Mth.cos((walkPhase * 3.1415927f) - 0.2f) * fLerp) * 5.0f));
    }
}
