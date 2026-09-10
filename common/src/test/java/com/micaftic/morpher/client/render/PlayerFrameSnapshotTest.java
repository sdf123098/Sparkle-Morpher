package com.micaftic.morpher.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerFrameSnapshotTest {

    @Test
    void sanitizePartialTickClampsAndGuardsNonFinite() {
        assertEquals(0.5f, PlayerFrameSnapshot.sanitizePartialTick(0.5f));
        assertEquals(0.0f, PlayerFrameSnapshot.sanitizePartialTick(-1.0f));
        assertEquals(1.0f, PlayerFrameSnapshot.sanitizePartialTick(2.0f));
        assertEquals(0.0f, PlayerFrameSnapshot.sanitizePartialTick(Float.NaN));
        assertEquals(0.0f, PlayerFrameSnapshot.sanitizePartialTick(Float.POSITIVE_INFINITY));
        assertEquals(0.0f, PlayerFrameSnapshot.sanitizePartialTick(Float.NEGATIVE_INFINITY));
    }

    @Test
    void carriesFrameInputs() {
        PlayerFrameSnapshot snapshot = PlayerFrameSnapshot.of(0.25f, 1.5f, 90.0f, -30.0f, 12.0f);
        assertEquals(0.25f, snapshot.walkAnimationSpeed());
        assertEquals(1.5f, snapshot.walkAnimationPos());
        assertEquals(90.0f, snapshot.bodyRot());
        assertEquals(-30.0f, snapshot.netHeadYaw());
        assertEquals(12.0f, snapshot.headPitch());
    }
}
