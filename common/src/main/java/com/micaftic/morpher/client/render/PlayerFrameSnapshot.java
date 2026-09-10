package com.micaftic.morpher.client.render;

/**
 * 玩家帧级渲染输入快照（路线图 §17 PlayerFrameSnapshot 雏形）。
 *
 * <p>承载单次渲染期间的瞬时输入（行走动画速度/位置、身体朝向、相对头 yaw、头 pitch），
 * 供 {@code AnimatableEntity} 组装 {@code AnimationEvent}、{@code ModernHudPoseStore} 发布
 * 姿态使用。它是纯数据，不含任何 gameplay / network / molang 副作用。</p>
 *
 * <p>长期实体状态仍归 {@code PlayerCapability}；本类型用于把「单次 render 临时状态」
 * 从长期 capability 中逐步剥离出来（§17 收口目标）。</p>
 */
public record PlayerFrameSnapshot(
        float walkAnimationSpeed,
        float walkAnimationPos,
        float bodyRot,
        float netHeadYaw,
        float headPitch
) {

    /** 非有限值按 0 处理，并夹取到 [0,1]（与历史 sanitizePartialTick 行为一致）。 */
    public static float sanitizePartialTick(float partialTick) {
        if (!Float.isFinite(partialTick)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, partialTick));
    }

    public static PlayerFrameSnapshot of(float walkAnimationSpeed, float walkAnimationPos,
                                        float bodyRot, float netHeadYaw, float headPitch) {
        return new PlayerFrameSnapshot(walkAnimationSpeed, walkAnimationPos, bodyRot, netHeadYaw, headPitch);
    }
}
