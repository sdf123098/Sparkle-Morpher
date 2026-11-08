package com.micaftic.morpher.core.api.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class ToolActionBridge {

    private ToolActionBridge() {
    }

    public static boolean canFishingRodCast(ItemStack stack) {
        return com.micaftic.morpher.core.api.item.fabric.ToolActionBridgeImpl.canFishingRodCast(stack);
    }

    public static boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        return com.micaftic.morpher.core.api.item.fabric.ToolActionBridgeImpl.onEntitySwing(stack, entity);
    }
}
