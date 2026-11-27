package com.micaftic.morpher.client.core;

import com.micaftic.morpher.client.motion.ClientActionMotionCursor;
import com.micaftic.morpher.client.motion.ClientMotionSources;
import com.micaftic.morpher.client.render.ModelScenePose;
import dev.spm.core.scene.CodedControllerCommand;
import dev.spm.core.scene.CodedLoopMode;
import dev.spm.core.scene.CodedPlayDecision;
import org.jetbrains.annotations.Nullable;

/**
 * Host controller slot descriptor and per-frame command builder, driving a corresponding member slot in
 * {@code spm-core}'s {@code CodedAnimationCollection} (and optional {@code PoseAnimationController}).
 */
public class HostControllerSlot<T extends HostAnimatable<?>> {

    public static final String PLAYER_SWING_CONTROLLER = "player.swing";
    public static final String PLAYER_CAP_CONTROLLER = "player.cap";

    private final T animatable;
    private final String name;
    private final double defaultTransitionTicks;
    private final boolean additive;
    private final HostActionPredicate<T> predicate;
    private final ClientActionMotionCursor synchronizedAction = new ClientActionMotionCursor();

    private String requestedAnimation = "";
    private CodedLoopMode requestedLoopMode = CodedLoopMode.CLIP_DEFAULT;
    private double requestedTransitionTicks;
    private double requestedSpeed = 1.0;
    private boolean pendingReset;
    private boolean pendingReload;
    private String currentAnimationName = "";
    private boolean currentlyPlaying;
    private double currentAnimTimeSeconds;
    private boolean finished = true;

    public HostControllerSlot(T animatable, String name, float transitionLengthTicks, HostActionPredicate<T> predicate) {
        this(animatable, name, transitionLengthTicks, predicate, false);
    }

    public HostControllerSlot(
            T animatable,
            String name,
            float transitionLengthTicks,
            HostActionPredicate<T> predicate,
            boolean additive) {
        this.animatable = animatable;
        this.name = name;
        this.defaultTransitionTicks = Math.max(0.0, transitionLengthTicks);
        this.requestedTransitionTicks = this.defaultTransitionTicks;
        this.predicate = predicate;
        this.additive = additive;
    }

    public T getAnimatable() {
        return this.animatable;
    }

    public String getName() {
        return this.name;
    }

    public double defaultTransitionTicks() {
        return this.defaultTransitionTicks;
    }

    public boolean isAdditive() {
        return this.additive;
    }

    public void setAnimation(String animationName) {
        setAnimation(animationName, null);
    }

    public void setAnimation(String animationName, @Nullable CodedLoopMode loopMode) {
        this.requestedAnimation = animationName == null ? "" : animationName;
        this.requestedLoopMode = loopMode == null ? CodedLoopMode.CLIP_DEFAULT : loopMode;
    }

    public void setTransitionLengthTicks(double ticks) {
        if (Double.isFinite(ticks) && ticks >= 0.0) {
            this.requestedTransitionTicks = ticks;
        }
    }

    private static final double MIN_PLAYBACK_SPEED = 1.0E-4;
    private static final double MAX_PLAYBACK_SPEED = 64.0;

    public void setAnimationTickOverride(double tickSpeedMultiplier) {
        if (Double.isFinite(tickSpeedMultiplier) && tickSpeedMultiplier > 0.0) {
            this.requestedSpeed = Math.max(MIN_PLAYBACK_SPEED, Math.min(MAX_PLAYBACK_SPEED, tickSpeedMultiplier));
        }
    }

    public void setPlaybackSpeed(double speed) {
        setAnimationTickOverride(speed);
    }

    public void stopTransition() {
        this.pendingReload = true;
    }

    public void clearAnimation() {
        this.requestedAnimation = "";
        this.pendingReset = true;
    }

    public void reset() {
        this.requestedAnimation = "";
        this.requestedLoopMode = CodedLoopMode.CLIP_DEFAULT;
        this.requestedTransitionTicks = this.defaultTransitionTicks;
        this.requestedSpeed = 1.0;
        this.pendingReset = true;
        this.pendingReload = false;
        this.currentAnimationName = "";
        this.currentlyPlaying = false;
        this.currentAnimTimeSeconds = 0.0;
        this.finished = true;
        this.synchronizedAction.clear();
    }

    public boolean isPlaying() {
        return this.currentlyPlaying;
    }

    public String getCurrentAnimation() {
        return this.currentlyPlaying && !this.currentAnimationName.isEmpty()
                ? "Coded -> " + this.currentAnimationName
                : "Coded";
    }

    public String getActiveClipName() {
        return this.currentAnimationName;
    }

    public double getCurrentAnimTimeSeconds() {
        return this.currentAnimTimeSeconds;
    }

    public boolean isFinished() {
        return this.finished;
    }

    /**
     * Called by the host once a fixed simulation step has actually consumed the last command.
     * Core only applies {@code reset}/{@code reload} inside a fixed step; render frames that execute
     * zero steps (any frame rate above the step rate) must keep the request latched, otherwise a
     * swing restart requested on such a frame is silently lost.
     */
    public void acknowledgeCommandDelivery() {
        this.pendingReset = false;
        this.pendingReload = false;
    }

    public CodedControllerCommand evaluateCommand(HostActionEvent<T> event, boolean hasSoundOverride) {
        this.requestedSpeed = 1.0;
        event.setController(this);
        HostPlayState.Decision decision;
        try {
            if (PLAYER_CAP_CONTROLLER.equals(this.name)) {
                var motion = ClientMotionSources.motion(event.getAnimatable());
                if (this.synchronizedAction.update(motion)) {
                    this.pendingReset = true;
                    this.requestedAnimation = motion.animationKey() == null ? "" : motion.animationKey();
                }
            }
            if (hasSoundOverride && !(PLAYER_SWING_CONTROLLER.equals(this.name)
                    && com.micaftic.morpher.client.input.InputStateKey.isLocalAnyHandSwinging())) {
                decision = HostPlayState.Decision.BYPASS;
            } else {
                decision = this.predicate.predicate(event, this.animatable);
            }
        } finally {
            event.setController(null);
        }

        if (decision == HostPlayState.Decision.STOP) {
            this.requestedAnimation = "";
            this.pendingReload = false;
        }
        String resolvedAnimation = this.requestedAnimation;
        if (!resolvedAnimation.isEmpty() && this.animatable != null) {
            var library = this.animatable.resolveAnimationLibrary();
            if (library != null && !library.hasClip(resolvedAnimation)) {
                var fallback = this.animatable.getAnimation(resolvedAnimation);
                if (fallback != null && fallback.name() != null && library.hasClip(fallback.name())) {
                    resolvedAnimation = fallback.name();
                }
            }
        }
        CodedPlayDecision coreDecision = switch (decision) {
            case CONTINUE -> CodedPlayDecision.CONTINUE;
            case STOP -> CodedPlayDecision.STOP;
            case PAUSE -> CodedPlayDecision.PAUSE;
            case BYPASS -> CodedPlayDecision.BYPASS;
        };
        return new CodedControllerCommand(
                coreDecision,
                resolvedAnimation,
                this.requestedLoopMode,
                this.requestedTransitionTicks,
                this.requestedSpeed,
                this.pendingReset,
                this.pendingReload);
    }

    public void updateFeedback(String activeClipName, double localTimeSeconds, boolean contributing, boolean finished) {
        this.currentAnimationName = activeClipName == null ? "" : activeClipName;
        this.currentAnimTimeSeconds = localTimeSeconds;
        this.currentlyPlaying = contributing && !this.currentAnimationName.isEmpty();
        this.finished = finished;
    }

    /** Optional procedural post-step hook for subclasses (e.g. {@code VehicleRotationController}). */
    public void applyProceduralPose(HostActionEvent<T> event, ModelScenePose pose) {
    }
}
