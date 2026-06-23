package com.micaftic.morpher.geckolib3.core.molang.builtin.query;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.entity.EntityFunction;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public class Position extends EntityFunction {
    @Override
    public Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        int value = arguments.getAsInt(context, 0);
        float partialTicks = context.entity().animationEvent().getFrameTime();
        Entity entity = context.entity().entity();
        switch (value) {
            case 0:
                return Mth.lerp(partialTicks, entity.xo, entity.getX());
            case 1:
                return Mth.lerp(partialTicks, entity.yo, entity.getY());
            case 2:
                return Mth.lerp(partialTicks, entity.zo, entity.getZ());
            default:
                return null;
        }
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }
}