package com.micaftic.morpher.core.display;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerDisplayStateTest {
    @Test void unknownAndKnownEmptyEffectsRemainDistinctAndImmutable() {
        assertNull(PlayerDisplayState.UNKNOWN.effectAmplifiers());
        var effects = new HashMap<>(Map.of("minecraft:speed", 256));
        var state = new PlayerDisplayState(42, 18.5f, 20f, 17, effects, false, .25f, 0f, 1f, false);
        effects.clear();
        assertEquals(256, state.effectAmplifiers().get("minecraft:speed"));
        assertThrows(UnsupportedOperationException.class, () -> state.effectAmplifiers().clear());
        assertNotEquals(PlayerDisplayState.UNKNOWN,
                new PlayerDisplayState(null, null, null, null, Map.of(), null, null, null, null, null));
    }
    @Test void invalidHiddenInputsCannotBecomeRenderValues() {
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -1, 1_000_001})
            assertThrows(IllegalArgumentException.class, () -> new PlayerDisplayState(null, value, null, null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new PlayerDisplayState(null, null, null, 21, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new PlayerDisplayState(null, null, null, null, Map.of("minecraft:speed", 0), null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new PlayerDisplayState(null, null, null, null, null, null, 1.1f, null, null, null));
    }
}
