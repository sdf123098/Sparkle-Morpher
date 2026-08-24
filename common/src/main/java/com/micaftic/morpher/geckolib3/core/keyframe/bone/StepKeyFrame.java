package com.micaftic.morpher.geckolib3.core.keyframe.bone;

import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import org.joml.Vector3f;

public class StepKeyFrame extends BoneKeyFrame {

    private final Vector3v postPoint;

    public StepKeyFrame(float startTick, float totalTick, Vector3v beginPoint, Vector3v postPoint) {
        super(startTick, totalTick, beginPoint);
        this.postPoint = postPoint;
    }

    @Override
    public Vector3f evaluate(ExpressionEvaluator<?> evaluator, float percentCompleted) {
        if (isEnd(percentCompleted)) {
            return this.postPoint.eval(evaluator);
        }
        return this.beginPoint.eval(evaluator);
    }
}
