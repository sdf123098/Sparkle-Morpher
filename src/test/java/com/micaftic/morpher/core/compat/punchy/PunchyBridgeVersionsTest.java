package com.micaftic.morpher.core.compat.punchy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunchyBridgeVersionsTest {

    @Test
    void acceptsInspectedVersionWithMetadata() {
        assertTrue(PunchyBridgeVersions.isSupported("2.8g"));
        assertTrue(PunchyBridgeVersions.isSupported(" 2.8g "));
        assertTrue(PunchyBridgeVersions.isSupported("2.8g+1.21.1"));
    }

    @Test
    void rejectsUninspectedOrMissingVersions() {
        assertFalse(PunchyBridgeVersions.isSupported(null));
        assertFalse(PunchyBridgeVersions.isSupported(""));
        assertFalse(PunchyBridgeVersions.isSupported("2.8"));
        assertFalse(PunchyBridgeVersions.isSupported("2.8f"));
        assertFalse(PunchyBridgeVersions.isSupported("2.8g-beta"));
        assertFalse(PunchyBridgeVersions.isSupported("2.9"));
    }
}
