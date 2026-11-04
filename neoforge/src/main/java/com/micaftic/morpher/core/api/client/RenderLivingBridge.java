package com.micaftic.morpher.core.api.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

public final class RenderLivingBridge {

    private RenderLivingBridge() {
    }

    public static boolean firePre(LivingEntity entity, LivingEntityRenderer<?, ?, ?> renderer, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        return false;
    }

    public static void firePost(LivingEntity entity, LivingEntityRenderer<?, ?, ?> renderer, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
    }
}
