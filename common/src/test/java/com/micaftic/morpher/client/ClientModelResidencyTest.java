package com.micaftic.morpher.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientModelResidencyTest {
    @Test
    void trimThrottleIsOwnedAndResettable() {
        ClientModelResidency residency = new ClientModelResidency();
        assertTrue(residency.shouldTrimAt(1_000));
        assertFalse(residency.shouldTrimAt(1_500));
        assertTrue(residency.shouldTrimAt(2_000));
        residency.resetTrimThrottle();
        assertTrue(residency.shouldTrimAt(2_001));
    }

    @Test
    void modeChangesResetThrottleAndReturnPriorMode() {
        ClientModelResidency residency = new ClientModelResidency();
        assertNull(residency.updateLazyLoadingMode(true));
        assertTrue(residency.shouldTrimAt(5_000));
        assertTrue(residency.updateLazyLoadingMode(false));
        assertTrue(residency.shouldTrimAt(5_001));
        assertFalse(residency.shouldTrimAt(5_500));
    }
}
