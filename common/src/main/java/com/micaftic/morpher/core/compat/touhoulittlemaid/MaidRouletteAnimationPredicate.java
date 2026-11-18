package com.micaftic.morpher.core.compat.touhoulittlemaid;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;

public final class MaidRouletteAnimationPredicate implements IAnimationPredicate<MaidCapability> {
    @Override
    public HostPlayState.Decision predicate(HostActionEvent<MaidCapability> event) {
        String animation = event.getAnimatable().getRouletteAnimation();
        return animation == null || animation.isBlank()
                ? HostPlayState.Decision.STOP
                : IAnimationPredicate.predicate(event, animation);
    }
}
