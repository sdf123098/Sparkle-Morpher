package com.micaftic.morpher.core.compat.simplehats;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class SimpleHatsHelper {

    private SimpleHatsHelper() {
    }

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static ItemStack getHatItem(LivingEntity livingEntity) {
        throw new AssertionError();
    }
}
