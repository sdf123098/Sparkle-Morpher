package com.micaftic.morpher.core.compat.elytraslot;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class ElytraSlotCompat {

    private ElytraSlotCompat() {
    }

    public static boolean isLoaded() { return false;
    }

    public static ItemStack getElytraItem(LivingEntity livingEntity) {
        return ItemStack.EMPTY;
    }
}
