package com.micaftic.morpher.geckolib3.core.controller;

import com.micaftic.morpher.geckolib3.core.snapshot.BoneTopLevelSnapshot;
import com.micaftic.morpher.geckolib3.core.molang.context.AnimationContext;
import com.micaftic.morpher.geckolib3.core.util.TransitionVector3f;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import org.jetbrains.annotations.Nullable;

public interface BoneTransformProvider {
    BoneTopLevelSnapshot getBoneTarget();

    @Nullable
    TransitionVector3f getRotation(ExpressionEvaluator<AnimationContext<?>> evaluator);

    @Nullable
    TransitionVector3f getPosition(ExpressionEvaluator<AnimationContext<?>> evaluator);

    @Nullable
    TransitionVector3f getScale(ExpressionEvaluator<AnimationContext<?>> evaluator);
}