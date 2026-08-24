package com.micaftic.morpher.molang.parser.ast;

import org.jetbrains.annotations.NotNull;

/**
 * Bedrock Molang {@code this} 关键字。
 *
 * <p>求值为当前正在计算的通道（骨骼 rotation/position/scale）在该帧已有的值，
 * 用于 {@code -this} 增量叠加（基岩版动画直读支持）。渲染端在关键帧求值前通过
 * {@link com.micaftic.morpher.molang.runtime.ExpressionEvaluator#setCurrentChannelValues(float, float, float)}
 * 写入当前值；未写入时默认为 0（对不使用 {@code this} 的现有动画零影响）。</p>
 */
public record ThisExpression() implements Expression {

    @Override
    public <R> R visit(@NotNull ExpressionVisitor<R> visitor) {
        return visitor.visitThis(this);
    }

    @Override
    public String toString() {
        return "This";
    }
}
