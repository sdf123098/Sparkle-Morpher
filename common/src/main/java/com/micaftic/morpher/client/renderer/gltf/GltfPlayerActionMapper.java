package com.micaftic.morpher.client.renderer.gltf;

import com.micaftic.morpher.client.animation.ControllerActionResolver;
import com.micaftic.morpher.client.animation.PlayerActionSnapshot;
import com.micaftic.morpher.client.animation.PlayerActionState;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.resource.gltf.GltfAnimationController;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * glTF 独立渲染管线与 SparkleMorpher 统一动作判定（1.2.4 {@link ControllerActionResolver}）之间的映射。
 *
 * <p>glTF 模型不经过 {@code PlayerModelBundle} / geckolib 控制器，渲染侧只能拿到原始
 * {@link LivingEntity}。本类把实体状态轻量采集为 {@link PlayerActionSnapshot}（速度用
 * {@code getDeltaMovement()}，无 {@code EntityFrameStateTracker}），复用
 * {@link ControllerActionResolver#resolveState} 得到统一的 {@link PlayerActionState}，
 * 再映射为 {@link GltfAnimationController.State} 驱动 glTF 内嵌动画。</p>
 */
public final class GltfPlayerActionMapper {

    private GltfPlayerActionMapper() {
    }

    /** PlayerActionState → glTF 动画状态。全集映射，无遗漏。 */
    public static GltfAnimationController.State map(PlayerActionState action) {
        if (action == null) {
            return GltfAnimationController.State.IDLE;
        }
        return switch (action) {
            case NONE, IDLE -> GltfAnimationController.State.IDLE;
            case DEATH -> GltfAnimationController.State.DEATH;
            case RIPTIDE, SWIM -> GltfAnimationController.State.SWIM;
            case SLEEP -> GltfAnimationController.State.SLEEP;
            case CLIMB -> GltfAnimationController.State.CLIMB;
            case CLIMBING -> GltfAnimationController.State.CLIMBING;
            case LADDER_UP -> GltfAnimationController.State.LADDER_UP;
            case LADDER_STILLNESS -> GltfAnimationController.State.LADDER_STILLNESS;
            case LADDER_DOWN -> GltfAnimationController.State.LADDER_DOWN;
            case FLY -> GltfAnimationController.State.FLY;
            case ELYTRA_FLY -> GltfAnimationController.State.ELYTRA_FLY;
            case SWIM_STAND -> GltfAnimationController.State.SWIM_STAND;
            case ATTACKED -> GltfAnimationController.State.ATTACKED;
            case JUMP -> GltfAnimationController.State.JUMP;
            case SNEAK -> GltfAnimationController.State.SNEAK;
            case SNEAKING -> GltfAnimationController.State.SNEAKING;
            case RUN -> GltfAnimationController.State.RUN;
            case WALK -> GltfAnimationController.State.WALK;
            case RIDE -> GltfAnimationController.State.RIDE;
        };
    }

    /** 轻量动作判定：复用统一快照 → 状态映射，不经过 geckolib 追踪器。 */
    public static PlayerActionState resolveAction(LivingEntity entity) {
        if (entity == null) {
            return PlayerActionState.NONE;
        }
        double horizontal = Math.sqrt(entity.getDeltaMovement().x * entity.getDeltaMovement().x
                + entity.getDeltaMovement().z * entity.getDeltaMovement().z);
        boolean flying = entity instanceof Player player && player.getAbilities().flying
                && entity.getPose() != Pose.FALL_FLYING;
        boolean riding = entity.getVehicle() != null && entity.getVehicle().isAlive();
        PlayerActionSnapshot snapshot = PlayerActionSnapshot.of(
                entity.isDeadOrDying(),
                entity.isAutoSpinAttack(),
                entity.getPose() == Pose.SLEEPING,
                entity.isSwimming(),
                entity.getPose() == Pose.SWIMMING,
                entity.onClimbable(),
                flying,
                entity.getPose() == Pose.FALL_FLYING && entity.isFallFlying(),
                entity.isInWater(),
                entity.onGround(),
                entity.getPose() == Pose.CROUCHING,
                entity.isSprinting(),
                entity.hurtTime > 0,
                horizontal > ControllerActionResolver.MIN_MOVEMENT_SPEED,
                (float) entity.getDeltaMovement().y,
                riding
        );
        return ControllerActionResolver.resolveState(snapshot);
    }

    /**
     * 渲染侧合并动作状态与瞬态手部动作：骑乘/死亡/睡眠等优先级态优先，
     * 其次攻击/使用瞬态，再落到基础动作状态。
     */
    public static GltfAnimationController.State resolveForMotion(LivingEntity entity) {
        if (entity == null) {
            return GltfAnimationController.State.IDLE;
        }
        PlayerActionState action = resolveAction(entity);
        if (action == PlayerActionState.RIDE || action == PlayerActionState.DEATH
                || action == PlayerActionState.SLEEP) {
            return map(action);
        }
        if (InputStateKey.isAnyHandSwinging(entity)) {
            return GltfAnimationController.State.ATTACK;
        }
        if (InputStateKey.isUsingItem(entity, InputStateKey.getUsedItemHand(entity))) {
            return GltfAnimationController.State.USE;
        }
        return map(action);
    }
}
