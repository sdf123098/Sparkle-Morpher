package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;

public class PlayerBaseAnimationPredicate implements IAnimationPredicate<CustomPlayerEntity> {
    @Override
    public HostPlayState.Decision predicate(HostActionEvent<CustomPlayerEntity> event) {
        CustomPlayerEntity playerEntity = event.getAnimatable();
        if (playerEntity instanceof IPreviewAnimatable previewAnimatable) {
            if (previewAnimatable.getAnimationStateMachine().hasAnimation()) {
                return IAnimationPredicate.playLoopAnimation(event, previewAnimatable.getAnimationStateMachine().getCurrentAnimation());
            }
            return HostPlayState.Decision.STOP;
        }
        if (playerEntity.isModelSwitching()) {
            if (playerEntity.isDisabledState()) {
                playerEntity.enableModel();
                event.getController().stopTransition();
            }
            return IAnimationPredicate.predicate(event, playerEntity.getSelectedModelId());
        }
        return HostPlayState.Decision.STOP;
    }
}
