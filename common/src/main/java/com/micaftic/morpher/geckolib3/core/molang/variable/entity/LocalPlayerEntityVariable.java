package com.micaftic.morpher.geckolib3.core.molang.variable.entity;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.variable.IValueEvaluator;
import com.micaftic.morpher.geckolib3.core.molang.variable.LambdaVariable;
import net.minecraft.client.player.LocalPlayer;

public class LocalPlayerEntityVariable extends LambdaVariable<LocalPlayer> {
    public LocalPlayerEntityVariable(IValueEvaluator<?, IContext<LocalPlayer>> evaluator) {
        super(evaluator);
    }

    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof LocalPlayer;
    }
}