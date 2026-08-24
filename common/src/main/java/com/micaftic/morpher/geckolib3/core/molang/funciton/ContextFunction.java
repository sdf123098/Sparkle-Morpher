package com.micaftic.morpher.geckolib3.core.molang.funciton;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import com.micaftic.morpher.molang.runtime.Function;
import com.micaftic.morpher.molang.runtime.binding.ValueConversions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ContextFunction<TEntity> implements Function {
    protected boolean validateContext(IContext<?> context) {
        return true;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public final Object evaluate(@NotNull ExecutionContext<?> context, @NotNull ArgumentCollection arguments) {
        Object entity = context.entity();
        if (entity instanceof IContext && validateContext((IContext<?>) entity)) {
            return eval((ExecutionContext<IContext<TEntity>>) context, arguments);
        } else {
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public final float evaluateFloat(@NotNull ExpressionEvaluator<?> context, @NotNull ArgumentCollection arguments) {
        Object entity = context.entity();
        if (entity instanceof IContext && validateContext((IContext<?>) entity)) {
            return evalFloat((ExpressionEvaluator<IContext<TEntity>>) context, arguments);
        }
        return 0.0f;
    }

    protected abstract Object eval(ExecutionContext<IContext<TEntity>> context, ArgumentCollection arguments);

    /**
     * 原始 float 求值钩子，默认回退到 {@link #eval}。返回数值的子类应重写此方法直接返回 float。
     */
    protected float evalFloat(ExpressionEvaluator<IContext<TEntity>> context, ArgumentCollection arguments) {
        return ValueConversions.asFloat(eval(context, arguments));
    }
}
