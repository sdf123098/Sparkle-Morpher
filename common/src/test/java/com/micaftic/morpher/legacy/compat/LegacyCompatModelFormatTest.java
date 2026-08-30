package com.micaftic.morpher.legacy.compat;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyCompatModelFormatTest {
    @Test
    void rejectsMissingOrMalformedLegacyContainers() throws Exception {
        assertEquals(-1, LegacyCompatModelFormat.detectCryptoVersion(null));
        assertEquals(-1, LegacyCompatModelFormat.detectCryptoVersion(new byte[0]));
        assertTrue(LegacyCompatModelFormat.read(new byte[0]).isEmpty());
        assertEquals(Map.of(), LegacyCompatModelFormat.read(new byte[23]));
    }
}
