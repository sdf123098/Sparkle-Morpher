package com.micaftic.morpher.client.animation;

import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.geckolib3.core.EntityFrameStateTracker;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.function.BiPredicate;

/**
 * 1.2.4（§14.1）：基础动作动画注册表 —— 双轨收敛后的声明层。
 *
 * <p><b>历史（已废弃的双轨）</b>：本类曾各自从 {@code player}/{@code event}
 * 独立推导基础动作（climb 用 limbSwingAmount、ladder 用符号判定、run 缺 moving 判定等），
 * 与 {@link ControllerActionResolver} 的判定口径存在漂移。
 *
 * <p><b>现在</b>：只声明「动画名 / 循环 / 优先级」三要素，判定谓词统一走
 * {@link ControllerActionResolver}（唯一权威来源）。行为差异属收敛修复：
 * ladder 引入 ±0.01 死区、run/walk/sneak 以 moving（groundSpeed）为准、飞行判定复用
 * {@code PlayerCapability} 追踪器。
 *
 * @deprecated 双轨判定已收敛，本类仅保留为动画注册声明层；整体删除与替代见 1.2.8（R13）。
 */
@Deprecated
public class AnimationRegister {

    private static final List<PlayerActionState> REGISTERED_STATES = List.of(
            PlayerActionState.DEATH,
            PlayerActionState.RIPTIDE,
            PlayerActionState.SLEEP,
            PlayerActionState.SWIM,
            PlayerActionState.CLIMB,
            PlayerActionState.CLIMBING,
            PlayerActionState.LADDER_UP,
            PlayerActionState.LADDER_STILLNESS,
            PlayerActionState.LADDER_DOWN,
            PlayerActionState.ELYTRA_FLY,
            PlayerActionState.FLY,
            PlayerActionState.SWIM_STAND,
            PlayerActionState.ATTACKED,
            PlayerActionState.JUMP,
            PlayerActionState.SNEAK,
            PlayerActionState.SNEAKING,
            PlayerActionState.RUN,
            PlayerActionState.WALK,
            PlayerActionState.IDLE
    );

    private AnimationRegister() {
    }

    public static void registerAnimationState() {
        register(PlayerActionState.DEATH, ILoopType.EDefaultLoopTypes.PLAY_ONCE, Priority.HIGHEST);
        register(PlayerActionState.RIPTIDE, Priority.HIGHEST);
        register(PlayerActionState.SLEEP, Priority.HIGHEST);
        register(PlayerActionState.SWIM, Priority.HIGHEST);
        register(PlayerActionState.CLIMB, Priority.HIGHEST);
        register(PlayerActionState.CLIMBING, Priority.HIGHEST);
        register(PlayerActionState.LADDER_UP, Priority.HIGHEST);
        register(PlayerActionState.LADDER_STILLNESS, Priority.HIGHEST);
        register(PlayerActionState.LADDER_DOWN, Priority.HIGHEST);
        register(PlayerActionState.ELYTRA_FLY, Priority.HIGH);
        register(PlayerActionState.FLY, Priority.HIGH);
        register(PlayerActionState.SWIM_STAND, Priority.NORMAL);
        register(PlayerActionState.ATTACKED, ILoopType.EDefaultLoopTypes.PLAY_ONCE, 2);
        register(PlayerActionState.JUMP, Priority.NORMAL);
        register(PlayerActionState.SNEAK, Priority.NORMAL);
        register(PlayerActionState.SNEAKING, Priority.NORMAL);
        register(PlayerActionState.RUN, Priority.LOW);
        register(PlayerActionState.WALK, Priority.LOW);
        register(PlayerActionState.IDLE, Priority.LOWEST);
    }

    /**
     * 已声明的基础动作集合（供收敛测试核对注册完整性与唯一性）。
     * 不包含 {@link PlayerActionState#NONE}（占位）与 {@link PlayerActionState#RIDE}
     * （骑乘动画由 {@code ctrl.ride} 函数 / {@code AnimationManager} 骑乘守卫处理）。
     */
    static List<PlayerActionState> registeredStates() {
        return REGISTERED_STATES;
    }

    private static void register(PlayerActionState state, ILoopType loopType, int priority) {
        register(state.animationName(), loopType, priority, (player, event) -> isState(state, player, event));
    }

    private static void register(PlayerActionState state, int priority) {
        register(state, ILoopType.EDefaultLoopTypes.LOOP, priority);
    }

    private static void register(String animationName, ILoopType loopType, int priority, BiPredicate<Player, AnimationEvent<CustomPlayerEntity>> predicate) {
        AnimationManager.register(new AnimationState<>(animationName, loopType, priority, predicate));
    }

    /**
     * 统一判定入口：走 {@link ControllerActionResolver}，并按帧缓存结果，
     * 避免每个已注册状态重复做一次快照采集（每帧至多一次 MovementQuery 计算）。
     */
    private static boolean isState(PlayerActionState state, Player player, AnimationEvent<CustomPlayerEntity> event) {
        CustomPlayerEntity animatable = event.getAnimatable();
        EntityFrameStateTracker<?> tracker = animatable.getPositionTracker();
        String cached = tracker.getCachedControllerState();
        if (cached == null) {
            cached = ControllerActionResolver.resolve(animatable, player, event);
            tracker.setCachedControllerState(cached);
        }
        return state.animationName().equals(cached);
    }
}
