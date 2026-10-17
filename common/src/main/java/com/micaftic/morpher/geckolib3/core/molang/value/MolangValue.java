package com.micaftic.morpher.geckolib3.core.molang.value;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.molang.parser.ast.Expression;
import com.micaftic.morpher.molang.parser.ast.FloatExpression;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import com.micaftic.morpher.molang.runtime.binding.ValueConversions;

import java.util.List;

public class MolangValue implements IValue {

    private final List<Expression> expressions;
    private final String source;
    private final String failure;
    public List<Expression> expressions() { return expressions; }
    public String source() { return source; }
    public String failure() { return failure; }
    public boolean parsedSuccessfully() { return failure == null; }
    public static MolangValue failed(String source, Throwable error) {
        return new MolangValue(List.of(FloatExpression.ZERO), false, source, error.toString());
    }
    private Float snapshot(ExpressionEvaluator<?> evaluator) {
        return evaluator != null && evaluator.entity() instanceof com.micaftic.morpher.geckolib3.core.molang.context.AnimationContext<?> context
                ? context.programPoseValues.get(this) : null;
    }

    private final boolean isScript;

    private final boolean constant;
    private final float constFloat;
    private final Float constBoxed;

    // 非 script 且只有一条表达式时走 primitive 路径，省掉中间装箱
    private final Expression single;

    public MolangValue(List<Expression> list, boolean isScript) {
        this(list, isScript, "", null);
    }

    public MolangValue(List<Expression> list, boolean isScript, String source, String failure) {
        this.expressions = List.copyOf(list);
        this.source = source;
        this.failure = failure;
        this.isScript = isScript;

        Expression onlyOne = (!isScript && list != null && list.size() == 1) ? list.get(0) : null;
        this.single = onlyOne;

        if (onlyOne instanceof FloatExpression fe) {
            this.constant = true;
            this.constFloat = fe.value();
            this.constBoxed = fe.boxed();
        } else {
            this.constant = false;
            this.constFloat = 0.0f;
            this.constBoxed = null;
        }
    }

    @Override
    public float evalAsFloat(ExpressionEvaluator<?> evaluator) {
        Float cached = snapshot(evaluator);
        if (cached != null) return cached;
        if (this.constant) {
            return this.constFloat;
        }
        if (this.single != null) {
            try {
                return evaluator.evalAsFloat(this.single);
            } catch (Throwable th) {
                com.micaftic.morpher.client.animation.molang.EvaluationHealth.invalid();
                YesSteveModel.LOGGER.debug("Failed to evaluate molang expression.", th);
                return 0.0f;
            }
        }
        return ValueConversions.asFloat(evalSafe(evaluator));
    }

    @Override
    public boolean evalAsBoolean(ExpressionEvaluator<?> evaluator) {
        Float cached = snapshot(evaluator);
        if (cached != null) return cached != 0;
        if (this.constant) {
            return this.constFloat != 0.0f;
        }
        if (this.single != null) {
            try {
                return evaluator.evalAsBoolean(this.single);
            } catch (Throwable th) {
                com.micaftic.morpher.client.animation.molang.EvaluationHealth.invalid();
                YesSteveModel.LOGGER.debug("Failed to evaluate molang expression.", th);
                return false;
            }
        }
        return ValueConversions.asBoolean(evalSafe(evaluator));
    }

    @Override
    public Object evalUnsafe(ExpressionEvaluator<?> evaluator) {
        Float cached = snapshot(evaluator);
        if (cached != null) return cached;
        if (this.constant) {
            return this.constBoxed;
        }
        return evaluator.evalAll(this.expressions, this.isScript);
    }
}
