package com.micaftic.morpher.core.compat.simplehats;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class SimpleHatsHelper {

    private SimpleHatsHelper() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.simplehats.fabric.SimpleHatsHelperImpl.isLoaded();
    }

    public static ItemStack getHatItem(LivingEntity livingEntity) {
        return com.micaftic.morpher.core.compat.simplehats.fabric.SimpleHatsHelperImpl.getHatItem(livingEntity);
    }
}
