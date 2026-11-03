package com.micaftic.morpher.core.compat.simplehats;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class SimpleHatsHelper {

    private SimpleHatsHelper() {
    }

    public static boolean isLoaded() { return false;
    }

    public static ItemStack getHatItem(LivingEntity livingEntity) {
        return ItemStack.EMPTY;
    }
}
