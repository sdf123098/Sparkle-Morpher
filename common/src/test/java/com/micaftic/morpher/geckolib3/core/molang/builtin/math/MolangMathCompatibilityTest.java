package com.micaftic.morpher.geckolib3.core.molang.builtin.math;

import com.micaftic.morpher.molang.parser.ast.FloatExpression;
import com.micaftic.morpher.molang.parser.ast.Expression;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluatorImpl;
import com.micaftic.morpher.molang.runtime.Function;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.lang.reflect.Proxy;
import net.minecraft.util.RandomSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MolangMathCompatibilityTest {
    private ExpressionEvaluatorImpl<IContext<Object>> evaluator() {
        var random = RandomSource.create(7L);
        @SuppressWarnings("unchecked")
        var context = (IContext<Object>) Proxy.newProxyInstance(IContext.class.getClassLoader(),
                new Class<?>[]{IContext.class}, (proxy, method, args) -> {
                    if (method.getName().equals("random")) return random;
                    return null;
                });
        return new ExpressionEvaluatorImpl<>(context);
    }

    private Function.ArgumentCollection arguments(float... values) {
        List<Expression> expressions = new ArrayList<>();
        for (float value : values) expressions.add(new FloatExpression(value));
        return new Function.ArgumentCollection(expressions);
    }

    @Test
    void integerRandomIncludesBothEndpointsAndAcceptsEqualBounds() {
        var evaluator = evaluator();
        var function = new RandomInteger();
        var observed = new HashSet<Integer>();
        for (int i = 0; i < 128; i++) {
            observed.add(((Number) function.evaluate(evaluator, arguments(1, 2))).intValue());
            observed.add((int) function.evaluateFloat(evaluator, arguments(1, 2)));
        }
        assertEquals(java.util.Set.of(1, 2), observed);
        assertEquals(3, ((Number) function.evaluate(evaluator, arguments(3, 3))).intValue());
        assertEquals(3f, function.evaluateFloat(evaluator, arguments(3, 3)));
    }

    @Test
    void integerDiceAcceptFixedFacesInBothEvaluationPaths() {
        var evaluator = evaluator();
        var function = new DieRollInteger();
        assertEquals(8, ((Number) function.evaluate(evaluator, arguments(2, 4, 4))).intValue());
        assertEquals(8f, function.evaluateFloat(evaluator, arguments(2, 4, 4)));
    }

    @Test
    void integerRandomHandlesReversedAndWideBoundsWithoutOverflow() {
        var evaluator = evaluator();
        var function = new RandomInteger();
        for (int i = 0; i < 32; i++) {
            int value = ((Number) function.evaluate(evaluator, arguments(10, -10))).intValue();
            assertTrue(value >= -10 && value <= 10);
            function.evaluate(evaluator, arguments(Integer.MIN_VALUE, Integer.MAX_VALUE));
        }
    }

    @Test
    void integerDiceSumsDoNotWrapIntoNegativeValues() {
        var evaluator = evaluator();
        var function = new DieRollInteger();
        var arguments = arguments(2, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(4294967294d, ((Number) function.evaluate(evaluator, arguments)).doubleValue());
        assertEquals((float) 4294967294d, function.evaluateFloat(evaluator, arguments));
    }

    @Test
    void hermiteBlendPreservesIntermediateValuesInBothEvaluationPaths() {
        var arguments = new Function.ArgumentCollection(List.of(new FloatExpression(.5f)));
        var evaluator = new ExpressionEvaluatorImpl<>(null);
        var function = new HermitBlend();
        assertEquals(.5f, ((Number) function.evaluate(evaluator, arguments)).floatValue());
        assertEquals(.5f, function.evaluateFloat(evaluator, arguments));
    }
}
