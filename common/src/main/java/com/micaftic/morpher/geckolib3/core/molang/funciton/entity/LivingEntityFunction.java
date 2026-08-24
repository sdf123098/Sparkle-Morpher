package com.micaftic.morpher.geckolib3.core.molang.funciton.entity;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.ContextFunction;
import net.minecraft.world.entity.LivingEntity;

public abstract class LivingEntityFunction extends ContextFunction<LivingEntity> {
    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof LivingEntity;
    }
}