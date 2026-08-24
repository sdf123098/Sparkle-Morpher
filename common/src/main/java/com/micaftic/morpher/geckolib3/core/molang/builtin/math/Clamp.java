package com.micaftic.morpher.geckolib3.core.molang.builtin.math;

import com.micaftic.morpher.molang.runtime.ExecutionContext;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import com.micaftic.morpher.molang.runtime.Function;
import net.minecraft.util.Mth;

public class Clamp implements Function {
    @Override
    public Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
        return Mth.clamp(arguments.getAsFloat(context, 0),
                arguments.getAsFloat(context, 1),
                arguments.getAsFloat(context, 2));
    }

    @Override
    public float evaluateFloat(ExpressionEvaluator<?> context, ArgumentCollection arguments) {
        return Mth.clamp(arguments.getAsFloatRaw(context, 0),
                arguments.getAsFloatRaw(context, 1),
                arguments.getAsFloatRaw(context, 2));
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 3;
    }
}
