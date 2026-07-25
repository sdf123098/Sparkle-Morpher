package com.micaftic.morpher.core.compat.touhoulittlemaid;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;

public final class MaidStateAnimationPredicate implements IAnimationPredicate<MaidCapability> {
    private final boolean renderState;

    public MaidStateAnimationPredicate(boolean renderState) {
        this.renderState = renderState;
    }

    @Override
    public PlayState predicate(AnimationEvent<MaidCapability> event, ExpressionEvaluator<?> evaluator) {
        String animation = this.renderState
                ? TouhouLittleMaidCompat.getMaidRenderAnimation(event.getAnimatable().getEntity())
                : TouhouLittleMaidCompat.getMaidGameAnimation(event.getAnimatable().getEntity());
        return animation == null || animation.isBlank()
                ? PlayState.STOP
                : IAnimationPredicate.playLoopAnimation(event, animation);
    }
}
