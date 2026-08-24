package com.micaftic.morpher.geckolib3.core.molang.builtin.query;

import net.minecraft.core.registries.Registries;
import com.micaftic.morpher.core.compat.cosmeticarmorreworked.CosmeticArmorHelper;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.util.MolangUtils;
import com.micaftic.morpher.geckolib3.core.molang.funciton.entity.LivingEntityFunction;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class IsItemNameAny extends LivingEntityFunction {
    @Override
    public Object eval(ExecutionContext<IContext<LivingEntity>> context, ArgumentCollection arguments) {
        ResourceLocation key;
        EquipmentSlot slotType = MolangUtils.parseSlotType(context, arguments, 0);
        if (slotType == null) {
            return null;
        }
        ItemStack stack = CosmeticArmorHelper.getArmorItem(context.entity().entity(), slotType);
        if (!stack.isEmpty() && (key = BuiltInRegistries.ITEM.getKey(stack.getItem())) != null) {
            for (int i = 1; i < arguments.size(); i++) {
                ResourceLocation location = arguments.getResourceLocation(context, i);
                if (location == null) {
                    return null;
                }
                if (location.equals(key)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size >= 2;
    }
}
