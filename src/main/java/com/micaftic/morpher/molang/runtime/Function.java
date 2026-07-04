package com.micaftic.morpher.molang.runtime;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.molang.parser.ast.Expression;
import com.micaftic.morpher.molang.parser.ast.StringExpression;
import com.micaftic.morpher.molang.runtime.binding.ValueConversions;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@FunctionalInterface
public interface Function {
    /**
     * Executes this function with the given arguments.
     *
     * @param context   The execution context
     * @param arguments The arguments
     * @return The function result
     * @since 3.0.0
     */
    @Nullable Object evaluate(final @NotNull ExecutionContext<?> context, final @NotNull ArgumentCollection arguments);

    /**
     * 原始 float 求值路径，用于避免在「每帧 × 每 bone」的热路径上把结果装箱成 {@link Float}。
     * 默认实现回退到 {@link #evaluate} 并转换；返回数值的内置函数应重写此方法直接返回 float。
     */
    default float evaluateFloat(final @NotNull ExpressionEvaluator<?> context, final @NotNull ArgumentCollection arguments) {
        return ValueConversions.asFloat(evaluate(context, arguments));
    }

    default boolean validateArgumentSize(int size) {
        return true;
    }

    ArgumentCollection EMPTY_ARGUMENT = new ArgumentCollection(new ArrayList());

    Function NOOP = (executionContext, argumentCollection) -> null;

    class ArgumentCollection {

        private final List<Expression> arguments;

        public ArgumentCollection(List<Expression> arguments) {
            this.arguments = arguments;
        }

        public int size() {
            return this.arguments.size();
        }

        @Nullable
        public String getAsString(@NotNull ExecutionContext<?> ctx, final int index) {
            return ValueConversions.asString(ctx.evalSafe(this.arguments.get(index)));
        }

        public int getStringId(@NotNull ExecutionContext<?> ctx, final int index) {
            return ValueConversions.asStringId(ctx.evalSafe(this.arguments.get(index)));
        }

        public double getAsDouble(@NotNull ExecutionContext<?> ctx, final int index) {
            return ValueConversions.asDouble(ctx.evalSafe(this.arguments.get(index)));
        }

        public int getAsInt(@NotNull ExecutionContext<?> ctx, final int index) {
            return ValueConversions.asInt(ctx.evalSafe(this.arguments.get(index)));
        }

        public float getAsFloat(@NotNull ExecutionContext<?> ctx, final int index) {
            return ValueConversions.asFloat(ctx.evalSafe(this.arguments.get(index)));
        }

        public boolean getAsBoolean(@NotNull ExecutionContext<?> ctx, final int index) {
            return ValueConversions.asBoolean(ctx.evalSafe(this.arguments.get(index)));
        }

        /**
         * 原始 float 参数读取：走 {@link ExpressionEvaluator#evalAsFloat} 的 primitive 递归路径，
         * 跳过 {@code evalSafe} 的 try/catch 与中间 Float 装箱。仅当 ctx 是 {@link ExpressionEvaluator} 时可用。
         */
        public float getAsFloatRaw(@NotNull ExpressionEvaluator<?> ctx, final int index) {
            return ctx.evalAsFloat(this.arguments.get(index));
        }

        public boolean getAsBooleanRaw(@NotNull ExpressionEvaluator<?> ctx, final int index) {
            return ctx.evalAsBoolean(this.arguments.get(index));
        }

        @Nullable
        public Identifier getResourceLocation(@NotNull ExecutionContext<? extends IContext<?>> ctx, final int index) {
            Object obj;
            Object obj2 = getValue(ctx, index);
            if (obj2 instanceof StringExpression stringExpression) {
                if (stringExpression.getResourceLocation() != null) {
                    return stringExpression.getResourceLocation();
                }
                Identifier resourceLocationTryParse = Identifier.tryParse(stringExpression.getName());
                if (resourceLocationTryParse != null) {
                    stringExpression.setResourceLocation(resourceLocationTryParse);
                    return resourceLocationTryParse;
                }
                obj = stringExpression.getName();
            } else if (obj2 instanceof String str) {
                Identifier resourceLocationTryParse2 = Identifier.tryParse(str);
                if (resourceLocationTryParse2 != null) {
                    return resourceLocationTryParse2;
                }
                obj = str;
            } else {
                obj = obj2;
            }
            ctx.entity().logWarning("Illegal resource location: ", obj);
            return null;
        }

        @Nullable
        public Object getValue(@NotNull ExecutionContext<?> ctx, final int index) {
            return ctx.evalSafe(this.arguments.get(index));
        }

        public Expression getExpression(int i) {
            return this.arguments.get(i);
        }
    }
}