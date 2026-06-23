package com.micaftic.morpher.geckolib3.core.molang.funciton.entity;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.ContextFunction;
import net.minecraft.world.entity.Entity;

public abstract class EntityFunction extends ContextFunction<Entity> {
    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof Entity;
    }
}