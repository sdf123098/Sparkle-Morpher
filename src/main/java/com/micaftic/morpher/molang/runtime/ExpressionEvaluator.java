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
}