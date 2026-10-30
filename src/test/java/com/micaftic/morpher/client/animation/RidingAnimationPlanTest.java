package com.micaftic.morpher.client.animation;

import com.micaftic.morpher.geckolib3.core.builder.Animation;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.event.ParticleEventKeyFrame;
import com.micaftic.morpher.geckolib3.core.keyframe.BoneAnimation;
import com.micaftic.morpher.geckolib3.core.keyframe.bone.BoneKeyFrame;
import com.micaftic.morpher.geckolib3.core.keyframe.bone.Vector3v;
import com.micaftic.morpher.geckolib3.core.keyframe.event.EventKeyFrame;
import com.micaftic.morpher.geckolib3.core.molang.value.IValue;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import com.micaftic.morpher.util.data.TypedArrays;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.micaftic.morpher.client.animation.RidingAnimationPlan.Kind.*;
import static com.micaftic.morpher.client.animation.RidingAnimationPlan.Content.*;
import static org.junit.jupiter.api.Assertions.*;

class RidingAnimationPlanTest {
    private static Animation clip(String name, BoneAnimation... bones) {
        return new Animation(name, Double.POSITIVE_INFINITY, ILoopType.EDefaultLoopTypes.LOOP,
                null, null, null, null, bones, TypedArrays.events(List.of()),
                new ParticleEventKeyFrame[0], TypedArrays.events(List.of()));
    }

    private static BoneAnimation pose(String boneName) {
        IValue noEvaluation = evaluator -> { throw new AssertionError("Analysis executed a script"); };
        BoneKeyFrame frame = new BoneKeyFrame(0, 1, new Vector3v(noEvaluation, noEvaluation, noEvaluation)) {
            @Override public Vector3f evaluate(ExpressionEvaluator<?> evaluator, float percent) {
                throw new AssertionError("Analysis sampled a pose");
            }
        };
        return new BoneAnimation(boneName, List.of(frame), List.of(), List.of());
    }

    private static RidingAnimationPlan plan(Map<String, Animation> animations) {
        return RidingAnimationPlan.analyze(animations::get);
    }

    @Test void minecartAndCushionUseSitEvenWhenRideAlsoExists() {
        var plan = plan(Map.of("sit", clip("sit", pose("arbitrary-leg")), "ride", clip("ride")));
        assertEquals("sit", plan.selection(SEATED).animation());
        assertEquals(POSE, plan.selection(SEATED).content());
        assertFalse(plan.selection(SEATED).fallback());
        assertEquals("ride", plan.selection(STRADDLE).animation());
    }

    @Test void specializedBoatAndPigActionsRetainPriority() {
        var plan = plan(Map.of("sit", clip("sit"), "ride", clip("ride"),
                "boat", clip("boat"), "ride_pig", clip("ride_pig")));
        assertEquals("boat", plan.selection(BOAT).animation());
        assertEquals("ride_pig", plan.selection(PIG).animation());
    }

    @Test void rideOnlyModelsRemainUsableOnEveryVehicle() {
        var plan = plan(Map.of("ride", clip("ride", pose("any-bone"))));
        for (var kind : RidingAnimationPlan.Kind.values()) {
            assertEquals("ride", plan.selection(kind).animation());
            assertEquals(kind != STRADDLE, plan.selection(kind).fallback());
        }
    }

    @Test void sitOnlyModelsSupplyMissingBoatPigAndStraddleActions() {
        var plan = plan(Map.of("sit", clip("sit", pose("renamed-bone"))));
        for (var kind : RidingAnimationPlan.Kind.values()) assertEquals("sit", plan.selection(kind).animation());
    }

    @Test void missingActionsDoNotSubstituteUnrelatedLocomotion() {
        var plan = plan(Map.of("walk", clip("walk", pose("leg")), "idle", clip("idle")));
        for (var kind : RidingAnimationPlan.Kind.values()) {
            assertNull(plan.selection(kind).animation());
            assertEquals(MISSING, plan.selection(kind).content());
        }
    }

    @Test void emptyAuthoredSlotIsAnOptOutRatherThanAnExcuseToInjectRide() {
        var plan = plan(Map.of("sit", clip("sit"), "ride", clip("ride", pose("leg"))));
        assertEquals("sit", plan.selection(SEATED).animation());
        assertEquals(EMPTY, plan.selection(SEATED).content());
        assertFalse(plan.selection(SEATED).fallback());
    }

    @Test void controlOnlySlotRetainsItsEventsWithoutExecutingThemDuringAnalysis() {
        var sit = clip("sit");
        sit.customInstructionKeyframes.add(new EventKeyFrame<>(0,
                new IValue[]{evaluator -> { throw new AssertionError("Executed authored event"); }}));
        var plan = plan(Map.of("sit", sit, "ride", clip("ride", pose("leg"))));
        assertEquals("sit", plan.selection(SEATED).animation());
        assertEquals(EVENTS_ONLY, plan.selection(SEATED).content());
        assertEquals(1, sit.customInstructionKeyframes.size());
    }

    @Test void analysisIsBoundedAndIndependentOfRenderRate() {
        var calls = new AtomicInteger();
        var plan = RidingAnimationPlan.analyze(action -> { calls.incrementAndGet(); return clip(action, pose("x")); });
        assertEquals(4, calls.get());
        for (int frame = 0; frame < 1000; frame++) assertEquals("sit", plan.selection(SEATED).animation());
        assertEquals(4, calls.get());
        assertThrows(UnsupportedOperationException.class, () -> plan.selections().clear());
    }

    @Test void modelReloadCreatesAnIndependentPlanAndCannotReusePreviousChoices() {
        var clips = new HashMap<String, Animation>();
        clips.put("ride", clip("ride"));
        var before = plan(clips);
        clips.put("sit", clip("sit", pose("leg")));
        var after = plan(clips);
        assertEquals("ride", before.selection(SEATED).animation());
        assertEquals("sit", after.selection(SEATED).animation());
    }
}
