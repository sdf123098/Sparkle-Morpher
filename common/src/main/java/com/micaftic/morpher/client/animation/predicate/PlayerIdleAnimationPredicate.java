package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import org.apache.commons.lang3.StringUtils;

public class PlayerIdleAnimationPredicate implements IAnimationPredicate<CustomPlayerEntity> {
    @Override
    public HostPlayState.Decision predicate(HostActionEvent<CustomPlayerEntity> event) {
        String str = ((IPreviewAnimatable) event.getAnimatable()).getAnimationStateMachine().getQueuedAnimation();
        if (StringUtils.isNoneBlank(str)) {
            return IAnimationPredicate.playLoopAnimation(event, str);
        }
        return HostPlayState.Decision.STOP;
    }
}
