package com.micaftic.morpher.geckolib3.core.molang.funciton.entity;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.ContextFunction;
import net.minecraft.world.entity.projectile.arrow.Arrow;

public abstract class ArrowEntityFunction extends ContextFunction<net.minecraft.world.entity.projectile.arrow.Arrow> {
    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof net.minecraft.world.entity.projectile.arrow.Arrow;
    }
}