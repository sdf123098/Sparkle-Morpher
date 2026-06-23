package com.micaftic.morpher.geckolib3.core.keyframe.bone;

import com.micaftic.morpher.geckolib3.core.molang.value.IValue;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import org.joml.Vector3f;

public class Vector3v {

    private final IValue x;

    private final IValue y;

    private final IValue z;

    private final Vector3f vector;

    public Vector3v(IValue x, IValue y, IValue z) {
        this.x = x;
        this.y = y;
        this.z = z;

        this.vector = new Vector3f();
    }

    public Vector3f eval(ExpressionEvaluator<?> evaluator) {
        return this.vector.set(x.evalAsFloat(evaluator),
                y.evalAsFloat(evaluator),
                z.evalAsFloat(evaluator));
    }
}