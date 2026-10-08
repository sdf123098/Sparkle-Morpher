package com.micaftic.morpher.core.model.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GpuCacheTrimTrackerTest {
    @Test
    void aTrimCanOnlyBeCompletedOnceForItsAssembly() {
        GpuCacheTrimTracker<Object> tracker = new GpuCacheTrimTracker<>();
        Object assembly = new Object();

        assertTrue(tracker.begin("model", assembly));
        assertFalse(tracker.begin("model", assembly));
        assertTrue(tracker.complete("model", assembly));
        assertTrue(tracker.isTrimmed("model", assembly));
        assertTrue(tracker.isTrimmed("model"));
        assertFalse(tracker.begin("model", assembly));
    }

    @Test
    void cancelingAnOldAssemblyCannotClearTheReplacementRequest() {
        GpuCacheTrimTracker<Object> tracker = new GpuCacheTrimTracker<>();
        Object oldAssembly = new Object();
        Object newAssembly = new Object();

        assertTrue(tracker.begin("model", oldAssembly));
        assertFalse(tracker.cancel("model", newAssembly));
        assertTrue(tracker.cancel("model", oldAssembly));
        assertTrue(tracker.begin("model", newAssembly));
        assertFalse(tracker.isTrimmed("model", newAssembly));
        assertTrue(tracker.complete("model", newAssembly));
        assertTrue(tracker.isTrimmed("model", newAssembly));
        assertTrue(tracker.isTrimmed("model"));
        assertFalse(tracker.cancel("model", oldAssembly));
        assertTrue(tracker.isTrimmed("model", newAssembly));
    }

    @Test
    void completedTrimCanBeClearedWhenTheModelIsTouchedAgain() {
        GpuCacheTrimTracker<Object> tracker = new GpuCacheTrimTracker<>();
        Object assembly = new Object();

        assertTrue(tracker.begin("model", assembly));
        assertTrue(tracker.complete("model", assembly));
        tracker.clear("model");
        assertFalse(tracker.isTrimmed("model", assembly));
        assertFalse(tracker.isTrimmed("model"));
        assertTrue(tracker.begin("model", assembly));
    }
}
