package com.micaftic.morpher.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Shared 26.3 bridge for submitting third-person held and head items. */
public final class ItemRenderBridge {
    private ItemRenderBridge() {
    }

    public static void renderLivingItem(ItemModelResolver resolver, LivingEntity entity, ItemStack stack,
                                        ItemDisplayContext context, PoseStack poseStack,
                                        SubmitNodeCollector collector, int packedLight) {
        if (stack.isEmpty() || collector == null) return;
        ItemStackRenderState renderState = new ItemStackRenderState();
        resolver.updateForLiving(renderState, stack, context, entity);
        renderState.submit(poseStack, collector, packedLight, OverlayTexture.NO_OVERLAY, entity.getId());
    }
}
