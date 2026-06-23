package com.micaftic.morpher.geckolib3.core.molang.variable.entity;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.variable.IValueEvaluator;
import com.micaftic.morpher.geckolib3.core.molang.variable.LambdaVariable;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

public class AbstractArrowEntityVariable extends LambdaVariable<net.minecraft.world.entity.projectile.arrow.AbstractArrow> {
    public AbstractArrowEntityVariable(IValueEvaluator<?, IContext<net.minecraft.world.entity.projectile.arrow.AbstractArrow>> evaluator) {
        super(evaluator);
    }

    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow;
    }
}