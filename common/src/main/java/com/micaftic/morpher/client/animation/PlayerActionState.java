package com.micaftic.morpher.client.animation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.2.4（§14.1）：玩家基础动作的唯一权威来源。
 *
 * <p>消除 {@code ControllerActionResolver} 与 {@code AnimationRegister} 双轨基础动作判定：
 * 所有消费方（动画状态机、Molang {@code ctrl.*}、vanilla fallback、compat 扩展）
 * 必须从同一个 {@link PlayerActionSnapshot} 推导到同一个 {@code PlayerActionState}，
 * 不允许各自独立从 entity 重新判定。
 *
 * <p>{@link #animationName()} 保持与历史动画名完全一致（{@code "death"} / {@code "run"} …），
 * 以便动画资源、{@code ctrl.*} molang 变量与 {@code AnimationRegister} 注册表无缝衔接。
 */
public enum PlayerActionState {

    /** 无动作（预览实体 / 爬墙跑酷中 / 实体缺失），等价历史 {@code StringPool.EMPTY}。 */
    NONE(""),

    DEATH("death"),
    RIPTIDE("riptide"),
    SLEEP("sleep"),
    SWIM("swim"),
    CLIMB("climb"),
    CLIMBING("climbing"),
    LADDER_UP("ladder_up"),
    LADDER_STILLNESS("ladder_stillness"),
    LADDER_DOWN("ladder_down"),
    FLY("fly"),
    ELYTRA_FLY("elytra_fly"),
    SWIM_STAND("swim_stand"),
    ATTACKED("attacked"),
    JUMP("jump"),
    SNEAK("sneak"),
    SNEAKING("sneaking"),
    RUN("run"),
    WALK("walk"),
    IDLE("idle"),
    /** 骑乘存活载具。 */
    RIDE("ride");

    private final String animationName;

    PlayerActionState(String animationName) {
        this.animationName = animationName;
    }

    /** 与历史动画名 / molang 状态字符串一致的名称；{@link #NONE} 返回空串。 */
    @NotNull
    public String animationName() {
        return this.animationName;
    }

    /** 是否为无动作占位状态。 */
    public boolean isEmpty() {
        return this == NONE;
    }

    /**
     * 按动画名反查状态；未知 / 空名返回 {@link #NONE}。
     * 注意 {@code "ride"} 等新状态若未注册进 Molang 绑定，按 {@link #NONE} 处理是安全的。
     */
    @NotNull
    public static PlayerActionState fromName(@Nullable String animationName) {
        if (animationName == null || animationName.isEmpty()) {
            return NONE;
        }
        for (PlayerActionState state : values()) {
            if (state.animationName.equals(animationName)) {
                return state;
            }
        }
        return NONE;
    }
}
