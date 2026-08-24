package com.micaftic.morpher.client.animation;

import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import net.minecraft.world.entity.LivingEntity;

public class StopAnimationPredicate implements IAnimationPredicate<AnimatableEntity<? extends LivingEntity>> {

    public static final StopAnimationPredicate INSTANCE = new StopAnimationPredicate();

    @Override
    public PlayState predicate(AnimationEvent<AnimatableEntity<? extends LivingEntity>> event, ExpressionEvaluator<?> evaluator) {
        return PlayState.STOP;
    }
}