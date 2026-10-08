package com.micaftic.morpher.core.model.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenGenerationGateTest {
    @Test
    void completionAfterCloseDoesNotNotifyOldHost() {
        ScreenGenerationGate gate = new ScreenGenerationGate();
        long request = gate.capture();
        gate.invalidate();
        AtomicInteger notifications = new AtomicInteger();

        assertFalse(gate.completeIfCurrent(request, notifications::incrementAndGet));
        assertEquals(0, notifications.get());
    }

    @Test
    void reopenedGenerationAcceptsItsOwnCompletionOnly() {
        ScreenGenerationGate gate = new ScreenGenerationGate();
        long oldRequest = gate.capture();
        gate.invalidate();
        long newRequest = gate.capture();
        AtomicInteger notifications = new AtomicInteger();

        assertFalse(gate.completeIfCurrent(oldRequest, notifications::incrementAndGet));
        assertTrue(gate.completeIfCurrent(newRequest, notifications::incrementAndGet));
        assertEquals(1, notifications.get());
    }
}
