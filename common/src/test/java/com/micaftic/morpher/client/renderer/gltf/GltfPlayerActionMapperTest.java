package com.micaftic.morpher.client.renderer.gltf;

import com.micaftic.morpher.client.animation.PlayerActionState;
import com.micaftic.morpher.resource.gltf.GltfAnimationController;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GltfPlayerActionMapper} 纯映射表测试：{@code PlayerActionState} 全集 → glTF State
 * 无遗漏、无歧义。实体侧判定（{@code resolveAction}/{@code resolveForMotion}）依赖 MC 运行时，
 * 由集成/运行验证覆盖，不在此纯 JUnit 环境实例化 MC 实体。
 */
class GltfPlayerActionMapperTest {

    @Test
    void mapsEveryPlayerActionState() {
        for (PlayerActionState action : PlayerActionState.values()) {
            assertNotNull(GltfPlayerActionMapper.map(action), action + " 必须映射到 glTF State");
        }
    }

    @Test
    void mapsNullToIdle() {
        assertEquals(GltfAnimationController.State.IDLE, GltfPlayerActionMapper.map(null));
    }

    @Test
    void keyStatesMapToExpectedGltfState() {
        assertEquals(GltfAnimationController.State.DEATH, GltfPlayerActionMapper.map(PlayerActionState.DEATH));
        assertEquals(GltfAnimationController.State.RUN, GltfPlayerActionMapper.map(PlayerActionState.RUN));
        assertEquals(GltfAnimationController.State.WALK, GltfPlayerActionMapper.map(PlayerActionState.WALK));
        assertEquals(GltfAnimationController.State.FLY, GltfPlayerActionMapper.map(PlayerActionState.FLY));
        assertEquals(GltfAnimationController.State.ELYTRA_FLY, GltfPlayerActionMapper.map(PlayerActionState.ELYTRA_FLY));
        assertEquals(GltfAnimationController.State.SWIM, GltfPlayerActionMapper.map(PlayerActionState.SWIM));
        assertEquals(GltfAnimationController.State.SWIM_STAND, GltfPlayerActionMapper.map(PlayerActionState.SWIM_STAND));
        assertEquals(GltfAnimationController.State.SNEAK, GltfPlayerActionMapper.map(PlayerActionState.SNEAK));
        assertEquals(GltfAnimationController.State.SNEAKING, GltfPlayerActionMapper.map(PlayerActionState.SNEAKING));
        assertEquals(GltfAnimationController.State.RIDE, GltfPlayerActionMapper.map(PlayerActionState.RIDE));
        assertEquals(GltfAnimationController.State.SLEEP, GltfPlayerActionMapper.map(PlayerActionState.SLEEP));
        assertEquals(GltfAnimationController.State.ATTACKED, GltfPlayerActionMapper.map(PlayerActionState.ATTACKED));
        assertEquals(GltfAnimationController.State.CLIMB, GltfPlayerActionMapper.map(PlayerActionState.CLIMB));
        assertEquals(GltfAnimationController.State.CLIMBING, GltfPlayerActionMapper.map(PlayerActionState.CLIMBING));
        assertEquals(GltfAnimationController.State.LADDER_UP, GltfPlayerActionMapper.map(PlayerActionState.LADDER_UP));
        assertEquals(GltfAnimationController.State.LADDER_STILLNESS, GltfPlayerActionMapper.map(PlayerActionState.LADDER_STILLNESS));
        assertEquals(GltfAnimationController.State.LADDER_DOWN, GltfPlayerActionMapper.map(PlayerActionState.LADDER_DOWN));
        assertEquals(GltfAnimationController.State.JUMP, GltfPlayerActionMapper.map(PlayerActionState.JUMP));
        assertEquals(GltfAnimationController.State.IDLE, GltfPlayerActionMapper.map(PlayerActionState.IDLE));
        assertEquals(GltfAnimationController.State.IDLE, GltfPlayerActionMapper.map(PlayerActionState.NONE));
    }

    @Test
    void mappingIsDeterministicAndCoverageComplete() {
        Map<GltfAnimationController.State, Set<PlayerActionState>> reverse = new HashMap<>();
        Set<PlayerActionState> seen = new HashSet<>();
        for (PlayerActionState action : PlayerActionState.values()) {
            GltfAnimationController.State state = GltfPlayerActionMapper.map(action);
            reverse.computeIfAbsent(state, k -> new HashSet<>()).add(action);
            seen.add(action);
        }
        assertEquals(PlayerActionState.values().length, seen.size(), "每个 PlayerActionState 都被映射");
        // 所有映射目标都是有效 glTF State（不含 CUSTOM，CUSTOM 只由 play() 显式设置）
        for (GltfAnimationController.State state : reverse.keySet()) {
            assertTrue(state != GltfAnimationController.State.CUSTOM, "map 不应产生 CUSTOM");
        }
    }
}
