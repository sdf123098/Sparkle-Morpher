package com.micaftic.morpher.client.animation;

import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostActionPredicate;
import com.micaftic.morpher.client.core.HostAnimatable;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.core.HostControllerSlot;
import com.micaftic.morpher.client.core.HostPlayState;
import dev.spm.core.scene.CodedLoopMode;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface IAnimationPredicate<T extends HostAnimatable<?>> extends HostActionPredicate<T> {
    HostPlayState.Decision predicate(HostActionEvent<T> event);

    @Override
    default HostPlayState.Decision predicate(HostActionEvent<T> event, HostAnimatable<?> animatable) {
        return predicate(event);
    }

    @NotNull
    static <T extends HostAnimatable<?>> HostPlayState.Decision playAnimationWithLoop(
            HostActionEvent<T> event, String animationName, CodedLoopMode loopMode) {
        HostControllerSlot<T> controller = event.getController();
        if (controller != null) {
            controller.setAnimation(animationName, loopMode);
        }
        return HostPlayState.Decision.CONTINUE;
    }

    @NotNull
    static <P extends HostAnimatable<?>> HostPlayState.Decision predicate(
            HostActionEvent<P> event, String animationName) {
        HostControllerSlot<P> controller = event.getController();
        if (controller != null) {
            controller.setAnimation(animationName);
        }
        return HostPlayState.Decision.CONTINUE;
    }

    @NotNull
    static <P extends HostAnimatable<?>> HostPlayState.Decision playAnimationWithValid(
            HostActionEvent<P> event, String animationName, CodedLoopMode loopMode, int version) {
        HostControllerSlot<P> controller = event.getController();
        if (controller != null) {
            if (AnimationFormatValidator.validate(event, animationName, version)) {
                controller.setAnimation(animationName);
            } else {
                controller.setAnimation(animationName, loopMode);
            }
        }
        return HostPlayState.Decision.CONTINUE;
    }

    @NotNull
    static <T extends HostAnimatable<?>> HostPlayState.Decision playLoopAnimation(
            HostActionEvent<T> event, String str) {
        return playAnimationWithLoop(event, str, CodedLoopMode.LOOP);
    }

    /**
     * Plays a continuous hold/use animation, preserving explicit {@code hold_on_last_frame} or
     * {@code loop: true} on the clip while promoting {@code play_once} clips to {@code fallbackLoopMode}
     * so continuous states do not stop after a single cycle.
     */
    @NotNull
    static <T extends HostAnimatable<?>> HostPlayState.Decision playContinuousAnimation(
            HostActionEvent<T> event, String animationName) {
        return playContinuousAnimation(event, animationName, CodedLoopMode.LOOP);
    }

    @NotNull
    static <T extends HostAnimatable<?>> HostPlayState.Decision playContinuousAnimation(
            HostActionEvent<T> event, String animationName, CodedLoopMode fallbackLoopMode) {
        ClipSpec clip = event.getAnimatable().getAnimation(animationName);
        if (clip != null && (clip.loopMode() == CodedLoopMode.HOLD_ON_LAST_FRAME
                || clip.loopMode() == CodedLoopMode.LOOP)) {
            return playAnimationWithLoop(event, animationName, clip.loopMode());
        }
        return playAnimationWithLoop(event, animationName, fallbackLoopMode);
    }
}
