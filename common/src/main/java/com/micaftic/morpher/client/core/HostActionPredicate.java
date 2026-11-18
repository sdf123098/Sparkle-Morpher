package com.micaftic.morpher.client.core;

import com.micaftic.morpher.client.animation.AnimationFormatValidator;
import dev.spm.core.scene.CodedLoopMode;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface HostActionPredicate<T extends HostAnimatable<?>> {

    HostPlayState.Decision predicate(HostActionEvent<T> event, HostAnimatable<?> animatable);

    @NotNull
    static <T extends HostAnimatable<?>> HostPlayState.Decision playAnimationWithLoop(
            HostActionEvent<T> event, String animationName, CodedLoopMode loopMode) {
        if (event.getController() != null) {
            event.getController().setAnimation(animationName, loopMode);
        }
        return HostPlayState.Decision.CONTINUE;
    }

    @NotNull
    static <T extends HostAnimatable<?>> HostPlayState.Decision predicate(
            HostActionEvent<T> event, String animationName) {
        if (event.getController() != null) {
            event.getController().setAnimation(animationName);
        }
        return HostPlayState.Decision.CONTINUE;
    }

    @NotNull
    static <T extends HostAnimatable<?>> HostPlayState.Decision playAnimationWithValid(
            HostActionEvent<T> event, String animationName, CodedLoopMode loopMode, int version) {
        if (event.getController() != null) {
            if (AnimationFormatValidator.validate(event, animationName, version)) {
                event.getController().setAnimation(animationName);
            } else {
                event.getController().setAnimation(animationName, loopMode);
            }
        }
        return HostPlayState.Decision.CONTINUE;
    }

    @NotNull
    static <T extends HostAnimatable<?>> HostPlayState.Decision playLoopAnimation(
            HostActionEvent<T> event, String animationName) {
        return playAnimationWithLoop(event, animationName, CodedLoopMode.LOOP);
    }
}
