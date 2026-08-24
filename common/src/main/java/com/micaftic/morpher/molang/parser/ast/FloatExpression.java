package com.micaftic.morpher.molang.parser.ast;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class FloatExpression implements Expression {

    public static final FloatExpression ZERO = new FloatExpression(0.0f);

    public static final FloatExpression ONE = new FloatExpression(1.0f);

    private final float value;
    private final Float boxed;

    public FloatExpression(float value) {
        this.value = value;
        this.boxed = value;
    }

    public float value() {
        return this.value;
    }

    public Float boxed() {
        return this.boxed;
    }

    @Override
    public <R> R visit(@NotNull ExpressionVisitor<R> expressionVisitor) {
        return expressionVisitor.visitFloat(this);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj != null && getClass() == obj.getClass() && Float.compare(((FloatExpression) obj).value, this.value) == 0;
    }

    public int hashCode() {
        return Objects.hash(Float.valueOf(this.value));
    }
}