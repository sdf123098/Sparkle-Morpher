package com.micaftic.morpher.core.api.item;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class ToolActionBridge {

    private ToolActionBridge() {
    }

    @ExpectPlatform
    public static boolean canFishingRodCast(ItemStack stack) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        throw new AssertionError();
    }
}
