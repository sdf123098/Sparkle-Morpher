package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import org.apache.commons.lang3.StringUtils;

public class PlayerCustomAnimationPredicate implements IAnimationPredicate<CustomPlayerEntity> {
    @Override
    public HostPlayState.Decision predicate(HostActionEvent<CustomPlayerEntity> event) {
        IPreviewAnimatable previewAnimatable = (IPreviewAnimatable) event.getAnimatable();
        String str = previewAnimatable.getAnimationStateMachine().getPreviousAnimation();
        if (StringUtils.isNoneBlank(str)) {
            previewAnimatable.setCustomAnimationActive(true);
            return IAnimationPredicate.playLoopAnimation(event, str);
        }
        previewAnimatable.setCustomAnimationActive(false);
        event.getController().clearAnimation();
        return HostPlayState.Decision.STOP;
    }
}
