package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;

public class PlayerBaseAnimationPredicate implements IAnimationPredicate<CustomPlayerEntity> {
    @Override
    public PlayState predicate(AnimationEvent<CustomPlayerEntity> event, ExpressionEvaluator<?> evaluator) {
        CustomPlayerEntity playerEntity = event.getAnimatable();
        if (playerEntity instanceof IPreviewAnimatable previewAnimatable) {
            if (previewAnimatable.getAnimationStateMachine().hasAnimation()) {
                return IAnimationPredicate.playLoopAnimation(event, previewAnimatable.getAnimationStateMachine().getCurrentAnimation());
            }
            return PlayState.STOP;
        }
        if (playerEntity.isModelSwitching()) {
            if (playerEntity.isDisabledState()) {
                playerEntity.enableModel();
                event.getController().stopTransition();
            }
            return IAnimationPredicate.predicate(event, playerEntity.getSelectedModelId());
        }
        return PlayState.STOP;
    }
}