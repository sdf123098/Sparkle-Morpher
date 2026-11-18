package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostAnimatable;
import com.micaftic.morpher.client.core.HostPlayState;

public class NamedAnimationPredicate<T extends HostAnimatable<?>> implements IAnimationPredicate<T> {

    private final String animationName;

    public NamedAnimationPredicate(String animationName) {
        this.animationName = animationName;
    }

    @Override
    public HostPlayState.Decision predicate(HostActionEvent<T> event) {
        return IAnimationPredicate.playLoopAnimation(event, this.animationName);
    }
}
