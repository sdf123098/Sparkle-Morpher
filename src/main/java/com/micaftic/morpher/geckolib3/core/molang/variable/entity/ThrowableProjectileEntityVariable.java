package com.micaftic.morpher.geckolib3.core.molang.variable.entity;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.variable.IValueEvaluator;
import com.micaftic.morpher.geckolib3.core.molang.variable.LambdaVariable;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;

public class ThrowableProjectileEntityVariable extends LambdaVariable<net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile> {
    public ThrowableProjectileEntityVariable(IValueEvaluator<?, IContext<net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile>> evaluator) {
        super(evaluator);
    }

    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
    }
}