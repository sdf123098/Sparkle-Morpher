package com.micaftic.morpher.core.api.network.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CloudStateTest {
    @AfterEach
    void resetState() { CloudState.reset(); }

    @Test
    void initialStateIsDisconnectedWithoutError() {
        CloudState.reset();
        assertEquals(CloudConnectionStatus.DISCONNECTED, CloudState.snapshot().status());
        assertEquals(CloudErrorCode.NONE, CloudState.snapshot().error());
        assertFalse(CloudState.isAvailable());
    }

    @Test
    void stateCarriesErrorAndGenerationWithoutLegacyFallback() {
        CloudState.setStatus(CloudConnectionStatus.CONNECTING, CloudErrorCode.NONE, "local-dev");
        long connectingGeneration = CloudState.snapshot().generation();
        CloudState.setStatus(CloudConnectionStatus.DEGRADED, CloudErrorCode.SESSION_EXPIRED, "local-dev");
        assertTrue(CloudState.isAvailable());
        assertFalse(CloudState.snapshot().allowsMutations());
        assertEquals(CloudErrorCode.SESSION_EXPIRED, CloudState.snapshot().error());
        assertTrue(CloudState.snapshot().generation() > connectingGeneration);
    }

    @Test
    void readyCannotCarryError() {
        assertThrows(IllegalArgumentException.class, () ->
                CloudState.setStatus(CloudConnectionStatus.READY, CloudErrorCode.INTERNAL, "local-dev"));
    }
}
