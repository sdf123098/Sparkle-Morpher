package com.micaftic.morpher.geckolib3.core.molang.builtin.math;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.ContextFunction;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import net.minecraft.util.RandomSource;

public class RandomInteger extends ContextFunction<Object> {
    @Override
    public boolean validateArgumentSize(int size) {
        return size == 2;
    }

    @Override
    protected Object eval(ExecutionContext<IContext<Object>> context, ArgumentCollection arguments) {
        return nextInclusive(context.entity().random(), arguments.getAsInt(context, 0), arguments.getAsInt(context, 1));
    }

    @Override
    protected float evalFloat(ExpressionEvaluator<IContext<Object>> context, ArgumentCollection arguments) {
        return nextInclusive(context.entity().random(), arguments.getAsInt(context, 0), arguments.getAsInt(context, 1));
    }

    static int nextInclusive(RandomSource random, int first, int second) {
        int min = Math.min(first, second);
        int max = Math.max(first, second);
        if (min == max) return min;
        long range = (long) max - min + 1L;
        if (range <= Integer.MAX_VALUE) return min + random.nextInt((int) range);
        // A long bound also covers the complete signed-int interval without overflowing.
        long bits;
        long value;
        do {
            bits = random.nextLong() >>> 1;
            value = bits % range;
        } while (bits - value + (range - 1) < 0);
        return (int) (min + value);
    }
}
