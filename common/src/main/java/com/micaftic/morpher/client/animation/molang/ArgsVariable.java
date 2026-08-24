package com.micaftic.morpher.client.animation.molang;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import com.micaftic.morpher.molang.runtime.Variable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ArgsVariable implements Variable {

    public static final ArgsVariable INSTANCE = new ArgsVariable();

    @Override
    @Nullable
    public Object evaluate(@NotNull ExecutionContext<?> context) {
        Object entity = context.entity();
        if (entity instanceof IContext) {
            return ((IContext<?>) entity).getAnimationLayers();
        }
        return null;
    }
}