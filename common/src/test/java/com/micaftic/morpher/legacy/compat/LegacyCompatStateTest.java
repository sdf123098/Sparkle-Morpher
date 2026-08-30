package com.micaftic.morpher.legacy.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyCompatStateTest {

    @Test
    void facadeOwnsLegacySessionStateLifecycle() {
        LegacyCompatState.resetClientSession();
        assertFalse(LegacyCompatState.isClientComplete());
        assertFalse(LegacyCompatState.isOysmServer());
        assertFalse(LegacyCompatState.isAllowUpload());

        LegacyCompatState.markClientComplete();
        LegacyCompatState.setOysmServer(true);
        LegacyCompatState.setAllowUpload(true);

        assertTrue(LegacyCompatState.isClientComplete());
        assertTrue(LegacyCompatState.isOysmServer());
        assertTrue(LegacyCompatState.isAllowUpload());

        LegacyCompatState.resetClientSession();
        assertFalse(LegacyCompatState.isClientComplete());
        assertFalse(LegacyCompatState.isOysmServer());
        assertFalse(LegacyCompatState.isAllowUpload());
    }
}
