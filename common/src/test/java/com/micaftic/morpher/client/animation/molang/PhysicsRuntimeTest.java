package com.micaftic.morpher.client.animation.molang;

import com.micaftic.morpher.client.animation.molang.functions.physics.FirstOrder;
import com.micaftic.morpher.client.animation.molang.functions.physics.SecondOrder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhysicsRuntimeTest {
    @Test void analyticFiltersAgreeAcrossSamplingRates() {
        for (int fps : new int[]{15, 30, 60, 144, 240}) {
            FirstOrder first = new FirstOrder(0, .1f, true);
            first.setArgs(10, .1f, 0, 0);
            for (int i = 0; i < fps; i++) first.update(1f / fps);
            assertEquals(10 * (1 - Math.exp(-10)), first.getValue(), 2e-5);
            for (float damping : new float[]{0, .4f, 1, 2}) {
                SecondOrder second = new SecondOrder(0, .7f, damping, 0, true);
                SecondOrder reference = new SecondOrder(0, .7f, damping, 0, true);
                for (int i = 1; i <= fps; i++) {
                    second.setArgs(10f * i / fps, .7f, damping, 0);
                    second.update(1f / fps);
                }
                reference.setArgs(10, .7f, damping, 0);
                reference.update(1);
                assertEquals(reference.getValue(), second.getValue(), 3e-5, "fps="+fps+" damping="+damping);
            }
        }
    }

    @Test void analyticBoundaryParametersStayFinite() {
        for(float damping : new float[]{0, 1, 2, Float.MAX_VALUE}) {
            SecondOrder second = new SecondOrder(35, .7f, damping, 0, true);
            for(int i=0; i<1000; i++) { second.setArgs(50, .7f, damping, 0); second.update(1f/60); }
            assertTrue(Float.isFinite(second.getValue()));
        }
        SecondOrder zeroFrequency = new SecondOrder(35, 0, 0, 0, true);
        zeroFrequency.setArgs(50, 0, 0, 0);
        zeroFrequency.update(1f/60);
        assertEquals(50, zeroFrequency.getValue());
    }
    @Test void currentInputIsSampledOnceAndClockStartsAtZero() {
        PhysicsManager manager = new PhysicsManager();
        manager.update(0);
        assertEquals(0, manager.sampleFirst(7, 0, .1f));
        manager.update(1f / 3);
        float moved = manager.sampleFirst(7, 10, .1f);
        assertTrue(moved > 0 && moved < 10, "must use this step's input");
        assertEquals(moved, manager.sampleFirst(7, 100, .1f));
        manager.update(1f / 3);
        assertEquals(moved, manager.sampleFirst(7, -100, .1f));
    }

    @Test void timeDiscontinuityAndDormancyRebaseToCurrentInput() {
        PhysicsManager manager = new PhysicsManager();
        manager.update(0);
        manager.sampleSecond(1, 35, .7f, .8f, 1);
        manager.update(40);
        assertEquals(80, manager.sampleSecond(1, 80, .7f, .8f, 1));
        manager.update(1);
        assertEquals(20, manager.sampleSecond(1, 20, .7f, .8f, 1));
        manager.update(2);
        manager.update(3);
        manager.update(4);
        manager.update(5);
        assertEquals(90, manager.sampleSecond(1, 90, .7f, .8f, 1));
    }

    @Test void typesAndScopesCannotAliasThroughIntegerHashCollisions() {
        PhysicsManager manager = new PhysicsManager();
        manager.update(0);
        assertEquals(10, manager.sampleFirst(1, 10, .1f));
        assertEquals(35, manager.sampleSecond(1, 35, .7f, .8f, 1));
        PhysicsManager.pushScope(31);
        try { assertEquals(50, manager.sampleSecond(1, 50, .7f, .8f, 1)); }
        finally { PhysicsManager.popScope(); }
        PhysicsManager.pushScope(0);
        try { assertEquals(70, manager.sampleSecond(2, 70, .7f, .8f, 1)); }
        finally { PhysicsManager.popScope(); }
    }
    @Test void constantInputHasNoStartupImpulse() {
        FirstOrder first = new FirstOrder(10, .1f);
        SecondOrder second = new SecondOrder(35, .7f, .8f, 1);
        assertEquals(10, first.getValue(), 1e-5);
        assertEquals(35, second.getValue(), 1e-5);
        for (int i = 0; i < 120; i++) {
            first.update(1f / 60);
            second.update(1f / 60);
            assertEquals(10, first.getValue(), 1e-5);
            assertEquals(35, second.getValue(), 1e-5);
        }
    }

    @Test void zeroTimeHoldsStateAndInvalidInputDoesNotResetPose() {
        FirstOrder first = new FirstOrder(10, .1f);
        first.setArgs(50, .1f, 0, 0);
        first.update(0);
        assertEquals(10, first.getValue(), 1e-5);
        first.setArgs(Float.NaN, .1f, 0, 0);
        first.update(1f / 60);
        assertEquals(10, first.getValue(), 1e-5);
        SecondOrder second = new SecondOrder(35, .7f, .8f, 1);
        second.setArgs(Float.POSITIVE_INFINITY, .7f, .8f, 1);
        second.update(1f / 60);
        assertEquals(35, second.getValue(), 1e-5);
    }
}
