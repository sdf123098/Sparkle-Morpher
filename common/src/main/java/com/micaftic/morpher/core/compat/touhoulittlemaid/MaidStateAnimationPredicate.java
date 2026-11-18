package com.micaftic.morpher.core.compat.touhoulittlemaid;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;

public final class MaidStateAnimationPredicate implements IAnimationPredicate<MaidCapability> {
    private final boolean renderState;

    public MaidStateAnimationPredicate(boolean renderState) {
        this.renderState = renderState;
    }

    @Override
    public HostPlayState.Decision predicate(HostActionEvent<MaidCapability> event) {
        String animation = this.renderState
                ? TouhouLittleMaidCompat.getMaidRenderAnimation(event.getAnimatable().getEntity())
                : TouhouLittleMaidCompat.getMaidGameAnimation(event.getAnimatable().getEntity());
        return animation == null || animation.isBlank()
                ? HostPlayState.Decision.STOP
                : IAnimationPredicate.playLoopAnimation(event, animation);
    }
}
