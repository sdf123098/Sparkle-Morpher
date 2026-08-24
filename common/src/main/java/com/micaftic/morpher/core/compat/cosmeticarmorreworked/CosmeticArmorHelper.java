package com.micaftic.morpher.core.compat.cosmeticarmorreworked;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.micaftic.morpher.core.compat.elytraslot.ElytraSlotCompat;

public final class CosmeticArmorHelper {

    private CosmeticArmorHelper() {
    }

    public static ItemStack getArmorItem(LivingEntity entity, EquipmentSlot slot) {
        if (entity == null || slot == null) {
            return ItemStack.EMPTY;
        }
        return entity.getItemBySlot(slot);
    }

    public static ItemStack getElytraItem(LivingEntity livingEntity) {
        if (livingEntity == null) {
            return ItemStack.EMPTY;
        }
        if (ElytraSlotCompat.isLoaded()) {
            ItemStack stack = ElytraSlotCompat.getElytraItem(livingEntity);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        ItemStack chestStack = getArmorItem(livingEntity, EquipmentSlot.CHEST);
        return chestStack.is(Items.ELYTRA) ? chestStack : ItemStack.EMPTY;
    }
}
