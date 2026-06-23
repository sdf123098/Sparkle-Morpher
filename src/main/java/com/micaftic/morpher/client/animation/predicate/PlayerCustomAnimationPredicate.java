package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import org.apache.commons.lang3.StringUtils;

public class PlayerCustomAnimationPredicate implements IAnimationPredicate<CustomPlayerEntity> {
    @Override
    public PlayState predicate(AnimationEvent<CustomPlayerEntity> event, ExpressionEvaluator<?> evaluator) {
        IPreviewAnimatable previewAnimatable = (IPreviewAnimatable) event.getAnimatable();
        String str = previewAnimatable.getAnimationStateMachine().getPreviousAnimation();
        if (StringUtils.isNoneBlank(str)) {
            previewAnimatable.setCustomAnimationActive(true);
            return IAnimationPredicate.playLoopAnimation(event, str);
        }
        previewAnimatable.setCustomAnimationActive(false);
        event.getController().clearAnimation();
        event.getController().markDirty();
        return PlayState.STOP;
    }
}
