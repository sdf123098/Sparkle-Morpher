package com.micaftic.morpher.core.compat.elytraslot;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class ElytraSlotCompat {

    private ElytraSlotCompat() {
    }

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static ItemStack getElytraItem(LivingEntity livingEntity) {
        throw new AssertionError();
    }
}
