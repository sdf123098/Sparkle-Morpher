package com.micaftic.morpher.geckolib3.core.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundTransitionGateTest {

    @Test
    void heldSoundTrigger_isAllowedOnceUntilConditionFalls() {
        SoundTransitionGate gate = new SoundTransitionGate();

        assertTrue(gate.allow(10, 20, true));
        assertFalse(gate.allow(10, 20, true));
        assertFalse(gate.allow(10, 20, false));
        assertTrue(gate.allow(10, 20, true));
    }

    @Test
    void differentTransitions_haveIndependentTriggers() {
        SoundTransitionGate gate = new SoundTransitionGate();

        assertTrue(gate.allow(10, 20, true));
        assertTrue(gate.allow(10, 30, true));
        assertFalse(gate.allow(10, 20, true));
        assertFalse(gate.allow(10, 30, true));
    }
}
