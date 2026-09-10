package com.micaftic.morpher.client.render;

/**
 * 玩家渲染策略（路线图 §18 PlayerRenderPolicy 雏形）。
 *
 * <p>把「是否用自定义模型接管玩家渲染」的选择从渲染调用点中抽出：调用方先解析
 * self/other/spectator/模型就绪/第一人称抑制 等策略输入，再由本类给出纯决策，
 * {@code CustomPlayerRenderer} 只负责 transform / geometry / layer 提交。</p>
 *
 * <p>本类不引用任何 Minecraft / compat 类型，便于单元测试；compat 语义（PBR、
 * FirstPerson mod、RealCamera、PlayerAnimator）由调用方按现有规则预先折算为
 * {@link GateInputs#firstPersonSuppressionSatisfied()}。</p>
 */
public final class PlayerRenderPolicy {

    /** 策略输入（全部为已解析的布尔量）。 */
    public record GateInputs(
            boolean available,
            boolean self,
            boolean disableSelfModel,
            boolean disableOtherModel,
            boolean spectator,
            boolean modelActive,
            boolean firstPersonSuppressionSatisfied
    ) {
    }

    public enum Decision {
        /** 由自定义模型渲染器接管（调用方应取消原版渲染）。 */
        RENDER_CUSTOM,
        /** 使用原版渲染（自定义模型不接管）。 */
        USE_VANILLA
    }

    private PlayerRenderPolicy() {
    }

    /** 纯决策：与历史 {@code ReplacePlayerRenderEvent} 内联门控完全等价。 */
    public static Decision decide(GateInputs inputs) {
        if (!inputs.available()) {
            return Decision.USE_VANILLA;
        }
        if (inputs.self() && inputs.disableSelfModel()) {
            return Decision.USE_VANILLA;
        }
        if ((!inputs.self() && inputs.disableOtherModel()) || inputs.spectator()) {
            return Decision.USE_VANILLA;
        }
        if (!inputs.modelActive()) {
            return Decision.USE_VANILLA;
        }
        if (!inputs.firstPersonSuppressionSatisfied()) {
            return Decision.USE_VANILLA;
        }
        return Decision.RENDER_CUSTOM;
    }
}
