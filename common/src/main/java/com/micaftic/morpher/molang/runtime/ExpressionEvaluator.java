package com.micaftic.morpher.molang.runtime;

import com.micaftic.morpher.molang.parser.ast.Expression;
import com.micaftic.morpher.molang.runtime.binding.ObjectBinding;
import com.micaftic.morpher.molang.runtime.binding.ValueConversions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ExpressionEvaluator<TEntity> extends ExecutionContext<TEntity> {
    @NotNull
    static <TEntity> ExpressionEvaluator<TEntity> evaluator(@Nullable TEntity entity) {
        return new ExpressionEvaluatorImpl<>(entity);
    }

    @NotNull
    static ExpressionEvaluator evaluator() {
        return evaluator(ObjectBinding.EMPTY);
    }

    default float evalAsFloat(@NotNull Expression expression) {
        return ValueConversions.asFloat(eval(expression));
    }

    default boolean evalAsBoolean(@NotNull Expression expression) {
        return ValueConversions.asBoolean(eval(expression));
    }

    /**
     * Bedrock Molang {@code this} 关键字：当前通道值（默认 0）。
     * 骨骼动画关键帧求值前由渲染端写入（见 AnimationControllerRuntime.BoneBlendState）。
     */
    default float currentValue() {
        return 0f;
    }

    /** 设置当前求值的通道轴（0=x, 1=y, 2=z），供 {@code this} 逐轴读取。 */
    default void setCurrentAxis(int axis) {
    }

    /** 设置当前骨骼通道的已有值（x/y/z），供 {@code this} 读取。 */
    default void setCurrentChannelValues(float x, float y, float z) {
    }
}