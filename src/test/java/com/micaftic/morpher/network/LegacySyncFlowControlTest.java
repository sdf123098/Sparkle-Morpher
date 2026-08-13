package com.micaftic.morpher.network;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySyncFlowControlTest {
    @Test
    void comparesPendingBytesWithAByteLimit() {
        assertFalse(LegacySyncFlowControl.shouldWaitForDrain(65_536L, 65_536L));
        assertTrue(LegacySyncFlowControl.shouldWaitForDrain(65_537L, 65_536L));
    }

    @Test
    void aggregateReservationCannotExceedBudget() {
        AtomicLong reserved = new AtomicLong();
        long half = LegacySyncFlowControl.CLIENT_IN_FLIGHT_BYTES / 2L;

        assertTrue(LegacySyncFlowControl.tryReserve(reserved, half));
        assertTrue(LegacySyncFlowControl.tryReserve(reserved, half));
        assertFalse(LegacySyncFlowControl.tryReserve(reserved, 1L));

        LegacySyncFlowControl.release(reserved, half);
        assertEquals(half, reserved.get());
        assertTrue(LegacySyncFlowControl.tryReserve(reserved, 1L));
    }

    @Test
    void saturatedAdditionDoesNotWrap() {
        assertEquals(Long.MAX_VALUE, LegacySyncFlowControl.addSaturated(Long.MAX_VALUE - 10L, 64L));
    }
}
