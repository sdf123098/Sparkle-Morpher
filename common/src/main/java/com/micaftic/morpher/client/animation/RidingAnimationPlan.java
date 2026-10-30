package com.micaftic.morpher.client.animation;

import com.micaftic.morpher.geckolib3.core.builder.Animation;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Model-local riding capabilities, compiled once after animation assembly.
 * Vehicle semantics choose the preferred slot; missing slots use compatible authored clips.
 * Analysis never evaluates scripts or guesses a pose from model/bone names or rotation values.
 */
public final class RidingAnimationPlan {
    public enum Kind {
        SEATED("sit", "ride"),
        STRADDLE("ride", "sit"),
        PIG("ride_pig", "ride", "sit"),
        BOAT("boat", "sit", "ride");

        private final String[] candidates;

        Kind(String... candidates) {
            this.candidates = candidates;
        }
    }

    public enum Content { POSE, EVENTS_ONLY, EMPTY, MISSING }

    /** Empty and event-only authored slots are intentional and retain their original behavior. */
    public record Selection(String animation, Content content, boolean fallback) { }

    private final Map<Kind, Selection> selections;

    private RidingAnimationPlan(Map<Kind, Selection> selections) {
        this.selections = Collections.unmodifiableMap(new EnumMap<>(selections));
    }

    public static RidingAnimationPlan analyze(Function<String, Animation> resolver) {
        // Resolve each slot once, including the same aliases used by LivingAnimatable.
        Map<String, Animation> clips = new java.util.HashMap<>();
        for (String slot : new String[]{"sit", "ride", "ride_pig", "boat"}) {
            clips.put(slot, resolver.apply(slot));
        }
        Map<Kind, Selection> selections = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            Selection selected = new Selection(null, Content.MISSING, false);
            for (int i = 0; i < kind.candidates.length; i++) {
                String slot = kind.candidates[i];
                Animation clip = clips.get(slot);
                if (clip != null) {
                    selected = new Selection(slot, content(clip), i != 0);
                    break;
                }
            }
            selections.put(kind, selected);
        }
        return new RidingAnimationPlan(selections);
    }

    private static Content content(Animation animation) {
        for (var bone : animation.boneAnimations) {
            if (!bone.rotationKeyFrames.isEmpty() || !bone.positionKeyFrames.isEmpty()
                    || !bone.scaleKeyFrames.isEmpty()) {
                return Content.POSE;
            }
        }
        return animation.soundKeyFrames.isEmpty() && animation.particleKeyFrames.isEmpty()
                && animation.customInstructionKeyframes.isEmpty() ? Content.EMPTY : Content.EVENTS_ONLY;
    }

    public Selection selection(Kind kind) {
        return selections.get(kind);
    }

    public Map<Kind, Selection> selections() {
        return selections;
    }
}
