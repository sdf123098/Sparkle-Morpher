package com.micaftic.morpher.geckolib3.core.molang.variable;

public interface IValueEvaluator<TValue, TContext> {
    TValue eval(TContext ctx);
}