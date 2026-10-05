package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CloudAutoLoginGateTest {
    @Test void retriesFailuresAfterCooldownAndDoesNotOverlapRequests() {
        var gate = new CloudAutoLoginGate();
        assertTrue(gate.begin("cloud/player/1", 0));
        assertFalse(gate.begin("cloud/player/1", 120_000));
        gate.finish();
        assertFalse(gate.begin("cloud/player/1", 1));
        assertTrue(gate.begin("cloud/player/1", 60_000));
    }

    @Test void selectionAndAccountChangesDoNotInheritAnotherAttempt() {
        var gate = new CloudAutoLoginGate();
        assertTrue(gate.begin("official/player/1", 0));
        assertFalse(gate.begin("community/player/2", 1));
        gate.finish();
        assertTrue(gate.begin("community/player/2", 2));
        gate.finish();
        assertTrue(gate.begin("community/other-player/3", 3));
    }
}
