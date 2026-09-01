package com.micaftic.morpher.client.animation;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.2.4（§14.1）：双轨收敛验证 —— {@link AnimationRegister} 声明集合必须与
 * {@link ControllerActionResolver} 可产出的基础动作集合完全一致，且每个已注册
 * 状态都能被快照映射命中（声明与判定不再各自独立推导）。
 */
class AnimationRegisterConvergenceTest {

    /** 每个基础动作的典型触发快照（与 resolveState 语义一一对应）。 */
    private static final Map<PlayerActionState, PlayerActionSnapshot> TRIGGERS = new EnumMap<>(PlayerActionState.class);

    static {
        TRIGGERS.put(PlayerActionState.DEATH, snap(s -> s.deadOrDying(true)));
        TRIGGERS.put(PlayerActionState.RIPTIDE, snap(s -> s.riptide(true)));
        TRIGGERS.put(PlayerActionState.SLEEP, snap(s -> s.sleeping(true)));
        TRIGGERS.put(PlayerActionState.SWIM, snap(s -> s.swimming(true)));
        TRIGGERS.put(PlayerActionState.CLIMB, snap(s -> s.swimmingPose(true).moving(true)));
        TRIGGERS.put(PlayerActionState.CLIMBING, snap(s -> s.swimmingPose(true)));
        TRIGGERS.put(PlayerActionState.LADDER_UP, snap(s -> s.onClimbable(true).verticalSpeed(0.5f)));
        TRIGGERS.put(PlayerActionState.LADDER_STILLNESS, snap(s -> s.onClimbable(true)));
        TRIGGERS.put(PlayerActionState.LADDER_DOWN, snap(s -> s.onClimbable(true).verticalSpeed(-0.5f)));
        TRIGGERS.put(PlayerActionState.ELYTRA_FLY, snap(s -> s.elytraFlying(true)));
        TRIGGERS.put(PlayerActionState.FLY, snap(s -> s.flying(true)));
        TRIGGERS.put(PlayerActionState.SWIM_STAND, snap(s -> s.inWater(true)));
        TRIGGERS.put(PlayerActionState.ATTACKED, snap(s -> s.attacked(true)));
        TRIGGERS.put(PlayerActionState.JUMP, snap(s -> s.moving(true)));
        TRIGGERS.put(PlayerActionState.SNEAK, snap(s -> s.onGround(true).crouching(true).moving(true)));
        TRIGGERS.put(PlayerActionState.SNEAKING, snap(s -> s.onGround(true).crouching(true)));
        TRIGGERS.put(PlayerActionState.RUN, snap(s -> s.onGround(true).sprinting(true).moving(true)));
        TRIGGERS.put(PlayerActionState.WALK, snap(s -> s.onGround(true).moving(true)));
        TRIGGERS.put(PlayerActionState.IDLE, snap(s -> s.onGround(true)));
    }

    @Test
    void registrationCoversExactlyResolvableBaseStates() {
        Set<PlayerActionState> registered = new HashSet<>(AnimationRegister.registeredStates());
        assertEquals(TRIGGERS.keySet(), registered, "注册集合必须与 resolver 可产出集合完全一致");
        assertFalse(registered.contains(PlayerActionState.NONE), "NONE 是占位状态，不应注册");
        assertFalse(registered.contains(PlayerActionState.RIDE), "RIDE 由骑乘守卫/ctrl.ride 处理，不应注册");
    }

    @Test
    void registeredNamesAreUniqueAndRoundTrip() {
        Set<String> names = new HashSet<>();
        for (PlayerActionState state : AnimationRegister.registeredStates()) {
            assertTrue(names.add(state.animationName()), "重复注册: " + state.animationName());
            assertEquals(state, PlayerActionState.fromName(state.animationName()));
        }
        assertEquals(AnimationRegister.registeredStates().size(), names.size());
    }

    @Test
    void everyRegisteredStateIsReachableFromSnapshot() {
        for (PlayerActionState state : AnimationRegister.registeredStates()) {
            PlayerActionSnapshot trigger = TRIGGERS.get(state);
            assertEquals(state, ControllerActionResolver.resolveState(trigger),
                    "注册状态 " + state + " 必须能被快照映射命中");
        }
    }

    @Test
    void registeredNamesMatchHistoricalAnimationNames() {
        // 动画名必须与历史一致，动画资源 / ctrl.* molang 变量才不中断。
        Set<String> names = new HashSet<>();
        for (PlayerActionState state : AnimationRegister.registeredStates()) {
            names.add(state.animationName());
        }
        for (String historical : new String[] { "death", "riptide", "sleep", "swim", "climb", "climbing",
                "ladder_up", "ladder_stillness", "ladder_down", "elytra_fly", "fly", "swim_stand", "attacked",
                "jump", "sneak", "sneaking", "run", "walk", "idle" }) {
            assertTrue(names.contains(historical), "缺少历史动画名: " + historical);
        }
    }

    private static PlayerActionSnapshot snap(java.util.function.Consumer<PlayerActionSnapshot.Builder> configure) {
        PlayerActionSnapshot.Builder builder = PlayerActionSnapshot.builder();
        configure.accept(builder);
        return builder.build();
    }
}
