package com.micaftic.morpher.core.api.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

public final class RenderLivingBridge {
    private RenderLivingBridge() {}

    /** Loader-owned event dispatch, installed during client initialization. */
    public interface Dispatcher {
        boolean firePre(LivingEntity entity, LivingEntityRenderer<?, ?> renderer, float partialTick,
                        PoseStack poseStack, MultiBufferSource bufferSource, int packedLight);

        void firePost(LivingEntity entity, LivingEntityRenderer<?, ?> renderer, float partialTick,
                      PoseStack poseStack, MultiBufferSource bufferSource, int packedLight);
    }

    private static volatile Dispatcher dispatcher;

    public static void install(Dispatcher dispatcher) {
        RenderLivingBridge.dispatcher = dispatcher;
    }

    public static boolean firePre(LivingEntity entity, LivingEntityRenderer<?, ?> renderer, float partialTick,
                                  PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Dispatcher current = dispatcher;
        return current != null && current.firePre(entity, renderer, partialTick, poseStack, bufferSource, packedLight);
    }

    public static void firePost(LivingEntity entity, LivingEntityRenderer<?, ?> renderer, float partialTick,
                                PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Dispatcher current = dispatcher;
        if (current != null) {
            current.firePost(entity, renderer, partialTick, poseStack, bufferSource, packedLight);
        }
    }
}
