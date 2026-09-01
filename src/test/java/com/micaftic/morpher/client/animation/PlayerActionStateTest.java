package com.micaftic.morpher.client.animation;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.2.4（§14.1）：{@link PlayerActionState} 枚举标识一致性。
 */
class PlayerActionStateTest {

    @Test
    void noneUsesEmptyAnimationName() {
        assertEquals("", PlayerActionState.NONE.animationName());
        assertTrue(PlayerActionState.NONE.isEmpty());
        assertFalse(PlayerActionState.DEATH.isEmpty());
    }

    @Test
    void allNonNoneStatesHaveNonBlankAnimationName() {
        for (PlayerActionState state : PlayerActionState.values()) {
            if (state == PlayerActionState.NONE) {
                continue;
            }
            assertFalse(state.animationName().isBlank(), state + " 的动画名不能为空");
        }
    }

    @Test
    void animationNamesAreUnique() {
        Set<String> names = new HashSet<>();
        for (PlayerActionState state : PlayerActionState.values()) {
            assertTrue(names.add(state.animationName()), "重复动画名: " + state.animationName());
        }
    }

    @Test
    void fromNameRoundTripsEveryState() {
        for (PlayerActionState state : PlayerActionState.values()) {
            assertEquals(state, PlayerActionState.fromName(state.animationName()));
        }
    }

    @Test
    void fromNameMapsUnknownOrBlankToNone() {
        assertEquals(PlayerActionState.NONE, PlayerActionState.fromName(null));
        assertEquals(PlayerActionState.NONE, PlayerActionState.fromName(""));
        assertEquals(PlayerActionState.NONE, PlayerActionState.fromName("not_a_state"));
    }

    @Test
    void coversRoadmapUnifiedActionSet() {
        // §14.1 统一动作集：death/sleep/swim/crawl/ladder/fly/jump/sneak/run/walk/idle/ride/use/swing
        // use/swing 由 HandRenderFunction（ctrl.hold/use/swing）在动作状态之外处理。
        Set<String> names = new HashSet<>();
        for (PlayerActionState state : PlayerActionState.values()) {
            names.add(state.animationName());
        }
        for (String required : new String[] { "death", "sleep", "swim", "climb", "climbing", "ladder_up",
                "ladder_stillness", "ladder_down", "fly", "elytra_fly", "jump", "sneak", "sneaking", "run",
                "walk", "idle", "ride", "swim_stand", "attacked", "riptide" }) {
            assertTrue(names.contains(required), "缺少统一动作状态: " + required);
        }
    }
}
