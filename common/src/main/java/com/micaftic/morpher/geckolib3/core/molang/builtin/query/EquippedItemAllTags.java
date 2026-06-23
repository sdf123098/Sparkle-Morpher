package com.micaftic.morpher.geckolib3.core.molang.builtin.query;

import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import com.micaftic.morpher.core.compat.cosmeticarmorreworked.CosmeticArmorHelper;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.util.MolangUtils;
import com.micaftic.morpher.geckolib3.core.molang.funciton.entity.LivingEntityFunction;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class EquippedItemAllTags extends LivingEntityFunction {
    @Override
    public Object eval(ExecutionContext<IContext<LivingEntity>> context, ArgumentCollection arguments) {
        EquipmentSlot slotType = MolangUtils.parseSlotType(context, arguments, 0);
        if (slotType == null) {
            return null;
        }
        ItemStack stack = CosmeticArmorHelper.getArmorItem(context.entity().entity(), slotType);
        if (stack.isEmpty()) {
            return false;
        }
        for (int i = 1; i < arguments.size(); i++) {
            Identifier key = arguments.getResourceLocation(context, i);
            if (key == null) {
                return null;
            }
            if (!stack.is(TagKey.create(Registries.ITEM, key))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size >= 2;
    }
}
