package com.micaftic.morpher.geckolib3.core.molang.builtin.math;

import com.micaftic.morpher.molang.runtime.ExecutionContext;
import com.micaftic.morpher.molang.runtime.Function;

public class Atan implements Function {
    @Override
    public Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
        return Math.atan(arguments.getAsDouble(context, 0));
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }
}