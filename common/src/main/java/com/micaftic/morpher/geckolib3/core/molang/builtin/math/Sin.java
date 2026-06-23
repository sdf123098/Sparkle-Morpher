package com.micaftic.morpher.geckolib3.core.molang.builtin.math;

import com.micaftic.morpher.molang.runtime.ExecutionContext;
import com.micaftic.morpher.molang.runtime.Function;
import net.minecraft.util.Mth;

public class Sin implements Function {
    @Override
    public Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
        return Mth.sin((arguments.getAsFloat(context, 0) / 180.0f) * 3.1415927f);
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }
}