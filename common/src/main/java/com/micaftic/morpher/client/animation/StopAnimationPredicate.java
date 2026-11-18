package com.micaftic.morpher.client.animation;

import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostAnimatable;
import com.micaftic.morpher.client.core.HostPlayState;

public class StopAnimationPredicate<T extends HostAnimatable<?>> implements IAnimationPredicate<T> {

    @SuppressWarnings("rawtypes")
    public static final StopAnimationPredicate INSTANCE = new StopAnimationPredicate<>();

    @Override
    public HostPlayState.Decision predicate(HostActionEvent<T> event) {
        return HostPlayState.Decision.STOP;
    }
}
