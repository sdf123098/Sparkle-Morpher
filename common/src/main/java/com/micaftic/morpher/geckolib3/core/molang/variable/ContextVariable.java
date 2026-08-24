package com.micaftic.morpher.geckolib3.core.molang.variable;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import com.micaftic.morpher.molang.runtime.Variable;

public abstract class ContextVariable<TEntity> implements Variable {
    protected boolean validateContext(IContext<?> context) {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final Object evaluate(ExecutionContext<?> context) {
        Object entity = context.entity();
        if (entity instanceof IContext && validateContext((IContext<?>) entity)) {
            return evaluate((IContext<TEntity>) entity);
        } else {
            return null;
        }
    }

    public abstract Object evaluate(IContext<TEntity> context);
}