package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.renderer.CustomPlayerRenderer;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.RendererManager;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.event.api.SpecialPlayerRenderEvent;
import com.elfmcys.yesstevemodel.geckolib3.geo.ModelRendererBridge;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.micaftic.morpher.core.architectury.event.EventResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class RenderFirstPlayerBackground {
    // 鍥犱负RenderHandEvent鍙湁鍑犵巼浼氭覆鏌撳娆★紝鎵€浠ヤ负浜嗛伩鍏嶅娆℃覆鏌擄紝杩欐牱璁捐
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
        if (GeneralConfig.safeGet(GeneralConfig.DISABLE_SELF_MODEL)) {
            return;
        }
        if (GeneralConfig.safeGet(GeneralConfig.DISABLE_SELF_HANDS)) {
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
            EventResult result = SpecialPlayerRenderEvent.post(new SpecialPlayerRenderEvent(player, cap, modelId));
            if (result.isFalse()) {
                return;
            }
            Identifier resourceLocationB_ = cap.getTextureLocation();
            int textureIndex = cap.getTextureIndex();
            VertexConsumer buffer = multiBufferSource.getBuffer(RenderTypes.entityCutout(resourceLocationB_));
            if (instance != null) {
                poseStack.pushPose();
                if (Minecraft.getInstance().options.bobView().get()) {
                    applyHandTransform(poseStack, partialTick, player);
                }
                poseStack.translate(0.0d, -1.5d, 0.0d);
                boolean previousFirstPersonMode = ModelPreviewRenderer.isFirstPerson();
                ModelPreviewRenderer.setFirstPersonMode(true);
                try {
                    ModelRendererBridge.renderMesh(buffer, poseStack.last(), modelAssembly.getAnimationBundle().getArmModel(), modelAssembly.getAnimationBundle().getArmModel().getBoneTransformData(), null, textureIndex, 3, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f, resourceLocationB_);
                } finally {
                    ModelPreviewRenderer.setFirstPersonMode(previousFirstPersonMode);
                    poseStack.popPose();
                }
            }
        });
    }

    private static void applyHandTransform(PoseStack poseStack, float partialTick, Player player) {
        float walkPhase = 0f;
        float fLerp = 0f;
        poseStack.translate(0.0d, 0.0d, 0.0d);
        poseStack.mulPose(Axis.ZN.rotationDegrees(0f));
        poseStack.mulPose(Axis.XN.rotationDegrees(0f));
    }
}
