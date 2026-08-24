package com.micaftic.morpher.molang.runtime;

import com.micaftic.morpher.molang.runtime.binding.ValueConversions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Variable {
    @Nullable
    Object evaluate(@NotNull ExecutionContext<?> context);

    /**
     * 原始 float 求值路径，避免在「每帧 × 每 bone」热路径上装箱 {@link Float}。
     * 默认回退到 {@link #evaluate} 并转换；返回数值的变量可重写此方法直接返回 float。
     */
    default float evaluateFloat(@NotNull ExecutionContext<?> context) {
        return ValueConversions.asFloat(evaluate(context));
    }
}
