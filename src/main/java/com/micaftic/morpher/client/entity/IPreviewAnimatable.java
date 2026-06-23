package com.micaftic.morpher.client.entity;

import com.micaftic.morpher.client.animation.AnimationTracker;
import org.jetbrains.annotations.NotNull;

public interface IPreviewAnimatable {
    @NotNull
    AnimationTracker getAnimationStateMachine();

    void setCustomAnimationActive(boolean active);
}