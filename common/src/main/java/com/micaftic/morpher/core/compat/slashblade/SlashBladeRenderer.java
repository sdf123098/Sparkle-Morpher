package com.micaftic.morpher.core.compat.slashblade;

import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Loader-neutral facade; see {@link SlashBladeCompat} for the gating scheme.
 * The {@code model} parameter is kept for signature compatibility with the
 * other hand-item render paths; the blade transform is entity-space, matching
 * where the caller invokes it.
 */
public final class SlashBladeRenderer {

    private SlashBladeRenderer() {
    }

    public static void renderOnEntity(LivingEntity entity, AnimatedGeoModel model, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, ItemStack stack, float partialTick) {
        if (!SlashBladeModState.LOADED) {
            return;
        }
        SlashBladeBridge.renderMainHandBlade(entity, stack, partialTick, poseStack, bufferSource, packedLight);
    }

    public static void renderRightWaist(AnimatedGeoModel model, LivingEntity entity, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, ItemStack stack) {
        if (!SlashBladeModState.LOADED) {
            return;
        }
        SlashBladeBridge.renderWaistBlade(stack, entity, poseStack, bufferSource, packedLight);
    }
}
