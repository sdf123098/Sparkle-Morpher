package com.micaftic.morpher.client.render;

import com.micaftic.morpher.client.render.PlayerRenderPolicy.Decision;
import com.micaftic.morpher.client.render.PlayerRenderPolicy.GateInputs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerRenderPolicyTest {

    private static GateInputs inputs(boolean self, boolean disableSelf, boolean disableOther,
                                     boolean spectator, boolean modelActive, boolean firstPersonSuppressed) {
        return new GateInputs(true, self, disableSelf, disableOther, spectator, modelActive, firstPersonSuppressed);
    }

    @Test
    void rendersCustomWhenModelActiveAndNotSuppressed() {
        assertEquals(Decision.RENDER_CUSTOM,
                PlayerRenderPolicy.decide(inputs(false, false, false, false, true, true)));
    }

    @Test
    void unavailableAlwaysFallsBackToVanilla() {
        assertEquals(Decision.USE_VANILLA, PlayerRenderPolicy.decide(
                new GateInputs(false, false, false, false, false, true, true)));
    }

    @Test
    void respectsSelfAndOtherDisableSwitches() {
        assertEquals(Decision.USE_VANILLA,
                PlayerRenderPolicy.decide(inputs(true, true, false, false, true, true)));
        assertEquals(Decision.USE_VANILLA,
                PlayerRenderPolicy.decide(inputs(false, false, true, false, true, true)));
        // Self disable must not block rendering other players.
        assertEquals(Decision.RENDER_CUSTOM,
                PlayerRenderPolicy.decide(inputs(false, true, false, false, true, true)));
        // Other disable must not block rendering self.
        assertEquals(Decision.RENDER_CUSTOM,
                PlayerRenderPolicy.decide(inputs(true, false, true, false, true, true)));
    }

    @Test
    void spectatorAndInactiveModelFallBackToVanilla() {
        assertEquals(Decision.USE_VANILLA,
                PlayerRenderPolicy.decide(inputs(false, false, false, true, true, true)));
        assertEquals(Decision.USE_VANILLA,
                PlayerRenderPolicy.decide(inputs(false, false, false, false, false, true)));
    }

    @Test
    void firstPersonWithoutSuppressionFallsBackToVanilla() {
        assertEquals(Decision.USE_VANILLA,
                PlayerRenderPolicy.decide(inputs(false, false, false, false, true, false)));
    }
}
