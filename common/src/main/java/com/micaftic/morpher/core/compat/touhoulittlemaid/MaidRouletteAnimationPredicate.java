package com.micaftic.morpher.core.compat.touhoulittlemaid;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;

public final class MaidRouletteAnimationPredicate implements IAnimationPredicate<MaidCapability> {
    @Override
    public PlayState predicate(AnimationEvent<MaidCapability> event, ExpressionEvaluator<?> evaluator) {
        String animation = event.getAnimatable().getRouletteAnimation();
        return animation == null || animation.isBlank()
                ? PlayState.STOP
                : IAnimationPredicate.predicate(event, animation);
    }
}
