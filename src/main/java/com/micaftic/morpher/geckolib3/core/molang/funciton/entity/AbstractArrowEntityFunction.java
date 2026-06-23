package com.micaftic.morpher.geckolib3.core.molang.funciton.entity;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.ContextFunction;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

public abstract class AbstractArrowEntityFunction extends ContextFunction<net.minecraft.world.entity.projectile.arrow.AbstractArrow> {
    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow;
    }
}