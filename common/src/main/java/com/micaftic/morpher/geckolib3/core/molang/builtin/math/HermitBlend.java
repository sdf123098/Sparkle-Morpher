package com.micaftic.morpher.geckolib3.core.molang.builtin.math;

import com.micaftic.morpher.molang.runtime.ExecutionContext;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import com.micaftic.morpher.molang.runtime.Function;

public class HermitBlend implements Function {
    @Override
    public Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
        float value = arguments.getAsFloat(context, 0);
        return value * value * (3.0f - 2.0f * value);
    }

    @Override
    public float evaluateFloat(ExpressionEvaluator<?> context, ArgumentCollection arguments) {
        float value = arguments.getAsFloatRaw(context, 0);
        return value * value * (3.0f - 2.0f * value);
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }
}
