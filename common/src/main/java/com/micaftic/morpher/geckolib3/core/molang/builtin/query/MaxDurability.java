package com.micaftic.morpher.geckolib3.core.molang.builtin.query;

import com.micaftic.morpher.core.compat.cosmeticarmorreworked.CosmeticArmorHelper;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.util.MolangUtils;
import com.micaftic.morpher.geckolib3.core.molang.funciton.entity.LivingEntityFunction;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import net.minecraft.world.entity.LivingEntity;

public class MaxDurability extends LivingEntityFunction {
    @Override
    public Object eval(ExecutionContext<IContext<LivingEntity>> context, ArgumentCollection arguments) {
        return CosmeticArmorHelper.getArmorItem(context.entity().entity(), MolangUtils.parseSlotType(context, arguments, 0)).getMaxDamage();
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }
}