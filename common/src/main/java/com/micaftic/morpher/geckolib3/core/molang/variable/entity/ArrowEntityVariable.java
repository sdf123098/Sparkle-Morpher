package com.micaftic.morpher.geckolib3.core.molang.variable.entity;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.variable.IValueEvaluator;
import com.micaftic.morpher.geckolib3.core.molang.variable.LambdaVariable;
import net.minecraft.world.entity.projectile.arrow.Arrow;

public class ArrowEntityVariable extends LambdaVariable<net.minecraft.world.entity.projectile.arrow.Arrow> {
    public ArrowEntityVariable(IValueEvaluator<?, IContext<net.minecraft.world.entity.projectile.arrow.Arrow>> evaluator) {
        super(evaluator);
    }

    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof net.minecraft.world.entity.projectile.arrow.Arrow;
    }
}