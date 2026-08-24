package com.micaftic.morpher.geckolib3.core.molang.variable.entity;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.variable.IValueEvaluator;
import com.micaftic.morpher.geckolib3.core.molang.variable.LambdaVariable;
import net.minecraft.world.entity.projectile.Arrow;

public class ArrowEntityVariable extends LambdaVariable<Arrow> {
    public ArrowEntityVariable(IValueEvaluator<?, IContext<Arrow>> evaluator) {
        super(evaluator);
    }

    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof Arrow;
    }
}