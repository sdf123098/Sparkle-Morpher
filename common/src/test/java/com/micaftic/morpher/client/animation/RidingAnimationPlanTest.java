package com.micaftic.morpher.client.animation;

import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.micaftic.morpher.client.animation.RidingAnimationPlan.Content.*;
import static com.micaftic.morpher.client.animation.RidingAnimationPlan.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class RidingAnimationPlanTest {
    private static ClipSpec clip(String name, RawYsmModel.RawBoneAnimation... bones) {
        var raw = new RawYsmModel.RawAnimation();
        raw.name = name;
        raw.length = Float.POSITIVE_INFINITY;
        raw.loopMode = 1;
        raw.boneAnimations.addAll(List.of(bones));
        return new ClipSpec(name, raw, List.of(), null, false, false);
    }

    private static RawYsmModel.RawBoneAnimation pose(String boneName) {
        var bone = new RawYsmModel.RawBoneAnimation();
        bone.boneName = boneName;
        var frame = new RawYsmModel.RawKeyframe();
        frame.postData = new Object[]{"query.should_never_evaluate()", "0", "0"};
        bone.rotation.add(frame);
        return bone;
    }

    private static RidingAnimationPlan plan(Map<String, ClipSpec> animations) {
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
        var event = new RawYsmModel.RawTimelineEvent();
        event.events.add("query.should_never_evaluate()");
        sit.raw().timelineEvents.add(event);
        var plan = plan(Map.of("sit", sit, "ride", clip("ride", pose("leg"))));
        assertEquals("sit", plan.selection(SEATED).animation());
        assertEquals(EVENTS_ONLY, plan.selection(SEATED).content());
        assertEquals(1, sit.raw().timelineEvents.size());
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
        var clips = new HashMap<String, ClipSpec>();
        clips.put("ride", clip("ride"));
        var before = plan(clips);
        clips.put("sit", clip("sit", pose("leg")));
        var after = plan(clips);
        assertEquals("ride", before.selection(SEATED).animation());
        assertEquals("sit", after.selection(SEATED).animation());
    }
}
