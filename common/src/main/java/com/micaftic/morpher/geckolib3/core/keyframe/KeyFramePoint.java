package com.micaftic.morpher.geckolib3.core.keyframe;

import com.micaftic.morpher.geckolib3.core.controller.AnimationControllerContext;
import com.micaftic.morpher.geckolib3.core.keyframe.bone.BoneKeyFrame;
import com.micaftic.morpher.geckolib3.core.molang.context.AnimationContext;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import org.joml.Vector3f;

public class KeyFramePoint extends AnimationPoint {

    public final BoneKeyFrame keyFrame;

    public KeyFramePoint(float currentTick, BoneKeyFrame keyFrame, AnimationControllerContext context) {
        super(currentTick, keyFrame.getTotalTick(), context);
        this.keyFrame = keyFrame;
    }

    @Override
    public Vector3f getLerpPoint(ExpressionEvaluator<AnimationContext<?>> evaluator) {
        setupControllerContext(evaluator);
        Vector3f vector3f = this.keyFrame.evaluate(evaluator, getPercentCompleted());
        if (this.cachedValue == null) {
            this.cachedValue = new Vector3f(vector3f);
        } else {
            this.cachedValue.set(vector3f);
        }
        return vector3f;
    }
}