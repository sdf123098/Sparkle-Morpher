package com.micaftic.morpher.core.compat.cosmeticarmorreworked;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class CosmeticArmorHelper {

    private CosmeticArmorHelper() {
    }

    @ExpectPlatform
    public static ItemStack getArmorItem(LivingEntity entity, EquipmentSlot slot) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static ItemStack getElytraItem(LivingEntity livingEntity) {
        throw new AssertionError();
    }
}
