package com.micaftic.morpher.core.compat.elytraslot;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class ElytraSlotCompat {

    private ElytraSlotCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.elytraslot.fabric.ElytraSlotCompatImpl.isLoaded();
    }

    public static ItemStack getElytraItem(LivingEntity livingEntity) {
        return com.micaftic.morpher.core.compat.elytraslot.fabric.ElytraSlotCompatImpl.getElytraItem(livingEntity);
    }
}
