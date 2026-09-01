package com.micaftic.morpher.client.animation;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.core.compat.parcool.ParcoolCompat;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.EntityFrameStateTracker;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.util.MovementQuery;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * 1.2.4（§14.1）：玩家基础动作的唯一权威判定入口。
 *
 * <p>快照采集（{@link #snapshot}）→ 状态映射（{@link #resolveState}）为纯函数，
 * 不产生 gameplay / network / molang 副作用；动画状态机、{@code ctrl.*} Molang
 * 绑定、vanilla fallback 与 compat 扩展都必须经由这里取得同一个
 * {@link PlayerActionState}，不允许各自独立从 entity 重新判定。
 *
 * <p>历史 String 常量与 {@code isState(String, ...)} 保留为兼容层（Molang 绑定、
 * 旧调用点），其值全部派生自 {@link PlayerActionState}，保证单一事实源。
 */
public final class ControllerActionResolver {

    public static final String DEATH = PlayerActionState.DEATH.animationName();
    public static final String RIPTIDE = PlayerActionState.RIPTIDE.animationName();
    public static final String SLEEP = PlayerActionState.SLEEP.animationName();
    public static final String SWIM = PlayerActionState.SWIM.animationName();
    public static final String CLIMB = PlayerActionState.CLIMB.animationName();
    public static final String CLIMBING = PlayerActionState.CLIMBING.animationName();
    public static final String LADDER_UP = PlayerActionState.LADDER_UP.animationName();
    public static final String LADDER_STILLNESS = PlayerActionState.LADDER_STILLNESS.animationName();
    public static final String LADDER_DOWN = PlayerActionState.LADDER_DOWN.animationName();
    public static final String FLY = PlayerActionState.FLY.animationName();
    public static final String ELYTRA_FLY = PlayerActionState.ELYTRA_FLY.animationName();
    public static final String SWIM_STAND = PlayerActionState.SWIM_STAND.animationName();
    public static final String ATTACKED = PlayerActionState.ATTACKED.animationName();
    public static final String JUMP = PlayerActionState.JUMP.animationName();
    public static final String SNEAK = PlayerActionState.SNEAK.animationName();
    public static final String SNEAKING = PlayerActionState.SNEAKING.animationName();
    public static final String RUN = PlayerActionState.RUN.animationName();
    public static final String WALK = PlayerActionState.WALK.animationName();
    public static final String IDLE = PlayerActionState.IDLE.animationName();

    public static final float MIN_MOVEMENT_SPEED = 0.05f;
    private static final float LADDER_STILLNESS_SPEED = 0.01f;

    private ControllerActionResolver() {
    }

    /** 带帧内缓存的状态解析（Molang / 渲染路径使用）。 */
    public static String resolve(IContext<LivingEntity> context) {
        LivingEntity entity = context.entity();
        AnimatableEntity<?> animatable = context.geoInstance();
        EntityFrameStateTracker<?> tracker = animatable.getPositionTracker();
        String cachedState = tracker.getCachedControllerState();
        if (cachedState != null) {
            return cachedState;
        }
        String state = resolve(animatable, entity, context.animationEvent());
        tracker.setCachedControllerState(state);
        return state;
    }

    /** String 兼容层：映射为统一的 {@link PlayerActionState} 后再取动画名。 */
    public static String resolve(AnimatableEntity<?> animatable, LivingEntity entity, AnimationEvent<?> event) {
        return resolveState(animatable, entity, event).animationName();
    }

    /** 唯一权威判定：直接返回状态枚举。 */
    public static PlayerActionState resolveState(AnimatableEntity<?> animatable, LivingEntity entity, AnimationEvent<?> event) {
        if (entity == null || animatable instanceof IPreviewAnimatable || isParcooling(entity)) {
            return PlayerActionState.NONE;
        }
        return resolveState(snapshot(animatable, entity, event));
    }

    /** 采集帧级动作快照（纯读取，无副作用）。 */
    public static PlayerActionSnapshot snapshot(AnimatableEntity<?> animatable, LivingEntity entity, AnimationEvent<?> event) {
        EntityFrameStateTracker<?> tracker = animatable.getPositionTracker();
        float groundSpeed = getGroundSpeed(entity, tracker, event);
        float verticalSpeed = getVerticalSpeed(entity, tracker);
        boolean moving = groundSpeed > MIN_MOVEMENT_SPEED;
        boolean onGround = entity.onGround();
        boolean inWater = entity.isInWater();
        boolean swimmingPose = entity.getPose() == Pose.SWIMMING;
        return new PlayerActionSnapshot(
                entity.isDeadOrDying(),
                entity.isAutoSpinAttack(),
                entity.getPose() == Pose.SLEEPING,
                entity.isSwimming(),
                swimmingPose,
                entity.onClimbable(),
                isFlying(animatable, entity),
                entity.getPose() == Pose.FALL_FLYING && entity.isFallFlying(),
                inWater,
                onGround,
                entity.getPose() == Pose.CROUCHING,
                entity.isSprinting(),
                entity.hurtTime > 0,
                moving,
                verticalSpeed,
                isRidingAliveVehicle(entity)
        );
    }

    /** 快照 → 状态映射（纯函数；RIDE 最先压制其余状态，与历史"骑乘即无动作"优先级一致）。 */
    public static PlayerActionState resolveState(PlayerActionSnapshot state) {
        if (state.riding()) {
            return PlayerActionState.RIDE;
        }
        if (state.deadOrDying()) {
            return PlayerActionState.DEATH;
        }
        if (state.riptide()) {
            return PlayerActionState.RIPTIDE;
        }
        if (state.sleeping()) {
            return PlayerActionState.SLEEP;
        }
        if (state.swimming()) {
            return PlayerActionState.SWIM;
        }
        if (state.swimmingPose() && state.moving()) {
            return PlayerActionState.CLIMB;
        }
        if (state.swimmingPose()) {
            return PlayerActionState.CLIMBING;
        }
        if (state.onClimbable()) {
            if (state.verticalSpeed() > LADDER_STILLNESS_SPEED) {
                return PlayerActionState.LADDER_UP;
            }
            if (state.verticalSpeed() < -LADDER_STILLNESS_SPEED) {
                return PlayerActionState.LADDER_DOWN;
            }
            return PlayerActionState.LADDER_STILLNESS;
        }
        if (state.elytraFlying()) {
            return PlayerActionState.ELYTRA_FLY;
        }
        if (state.flying()) {
            return PlayerActionState.FLY;
        }
        if (state.inWater() && !state.onGround()) {
            return PlayerActionState.SWIM_STAND;
        }
        if (state.attacked()) {
            return PlayerActionState.ATTACKED;
        }
        if (!state.onGround() && !state.inWater()) {
            return PlayerActionState.JUMP;
        }
        if (state.onGround() && state.crouching() && state.moving()) {
            return PlayerActionState.SNEAK;
        }
        if (state.onGround() && state.crouching()) {
            return PlayerActionState.SNEAKING;
        }
        if (state.onGround() && state.sprinting() && state.moving()) {
            return PlayerActionState.RUN;
        }
        if (state.onGround() && state.moving()) {
            return PlayerActionState.WALK;
        }
        return PlayerActionState.IDLE;
    }

    public static boolean isState(String expectedState, AnimatableEntity<?> animatable, LivingEntity entity, AnimationEvent<?> event) {
        return expectedState.equals(resolve(animatable, entity, event));
    }

    public static boolean isState(String expectedState, IContext<LivingEntity> context) {
        return expectedState.equals(resolve(context));
    }

    public static float getGroundSpeed(LivingEntity entity, EntityFrameStateTracker<?> tracker, AnimationEvent<?> event) {
        return MovementQuery.getGroundSpeed(entity, tracker, event);
    }

    public static float getVerticalSpeed(LivingEntity entity, EntityFrameStateTracker<?> tracker) {
        return MovementQuery.getVerticalSpeed(entity, tracker);
    }

    public static boolean isFlying(AnimatableEntity<?> animatable, LivingEntity entity) {
        if (entity.getPose() == Pose.FALL_FLYING && entity.isFallFlying()) {
            return false;
        }
        if (animatable instanceof PlayerCapability cap && !cap.isLocalPlayerModel()) {
            return cap.getPositionTracker().isFlying();
        }
        if (entity instanceof Player player) {
            return player.getAbilities().flying;
        }
        return false;
    }

    private static boolean isParcooling(LivingEntity entity) {
        return entity instanceof Player player && ParcoolCompat.isPlayerParcooling(player);
    }

    private static boolean isRidingAliveVehicle(LivingEntity entity) {
        Entity vehicle = entity.getVehicle();
        return vehicle != null && vehicle.isAlive();
    }
}
