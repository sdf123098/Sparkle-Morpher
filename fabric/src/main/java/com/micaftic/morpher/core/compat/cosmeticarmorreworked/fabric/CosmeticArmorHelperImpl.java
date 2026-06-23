package com.micaftic.morpher.core.compat.cosmeticarmorreworked.fabric;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;

public final class CosmeticArmorHelperImpl {

    private CosmeticArmorHelperImpl() {
    }

    public static ItemStack getArmorItem(LivingEntity entity, EquipmentSlot slot) {
        return entity.getItemBySlot(slot);
    }

    public static ItemStack getElytraItem(LivingEntity livingEntity) {
        ItemStack chest = livingEntity.getItemBySlot(EquipmentSlot.CHEST);
        return chest.getItem() instanceof ElytraItem ? chest : ItemStack.EMPTY;
    }
}
