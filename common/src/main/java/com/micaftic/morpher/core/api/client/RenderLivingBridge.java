package com.micaftic.morpher.core.api.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

public final class RenderLivingBridge {

    private RenderLivingBridge() {
    }

    public static boolean firePre(LivingEntity entity, LivingEntityRenderer<?, ?, ?> renderer, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        return com.micaftic.morpher.core.api.client.fabric.RenderLivingBridgeImpl.firePre(entity, renderer, partialTick, poseStack, bufferSource, packedLight);
    }

    public static void firePost(LivingEntity entity, LivingEntityRenderer<?, ?, ?> renderer, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        com.micaftic.morpher.core.api.client.fabric.RenderLivingBridgeImpl.firePost(entity, renderer, partialTick, poseStack, bufferSource, packedLight);
    }
}
