package com.micaftic.morpher.client.animation;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.EntityFrameStateTracker;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.geckolib3.util.MovementQuery;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import com.micaftic.morpher.core.compat.parcool.ParcoolCompat;

public final class ControllerActionResolver {

    public static final String DEATH = "death";
    public static final String RIPTIDE = "riptide";
    public static final String SLEEP = "sleep";
    public static final String SWIM = "swim";
    public static final String CLIMB = "climb";
    public static final String CLIMBING = "climbing";
    public static final String LADDER_UP = "ladder_up";
    public static final String LADDER_STILLNESS = "ladder_stillness";
    public static final String LADDER_DOWN = "ladder_down";
    public static final String FLY = "fly";
    public static final String ELYTRA_FLY = "elytra_fly";
    public static final String SWIM_STAND = "swim_stand";
    public static final String ATTACKED = "attacked";
    public static final String JUMP = "jump";
    public static final String SNEAK = "sneak";
    public static final String SNEAKING = "sneaking";
    public static final String RUN = "run";
    public static final String WALK = "walk";
    public static final String IDLE = "idle";

    public static final float MIN_MOVEMENT_SPEED = 0.05f;
    private static final float LADDER_STILLNESS_SPEED = 0.01f;

    private ControllerActionResolver() {
    }

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

    public static String resolve(AnimatableEntity<?> animatable, LivingEntity entity, AnimationEvent<?> event) {
        if (entity == null || animatable instanceof IPreviewAnimatable || isParcooling(entity) || isRidingAliveVehicle(entity)) {
            return StringPool.EMPTY;
        }
        return resolveState(snapshot(animatable, entity, event));
    }

    public static ActionSnapshot snapshot(AnimatableEntity<?> animatable, LivingEntity entity, AnimationEvent<?> event) {
        EntityFrameStateTracker<?> tracker = animatable.getPositionTracker();
        float groundSpeed = getGroundSpeed(entity, tracker, event);
        float verticalSpeed = getVerticalSpeed(entity, tracker);
        boolean moving = groundSpeed > MIN_MOVEMENT_SPEED;
        boolean onGround = entity.onGround();
        boolean inWater = entity.isInWater();
        boolean swimmingPose = entity.getPose() == Pose.SWIMMING;
        return new ActionSnapshot(
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
                verticalSpeed
        );
    }

    public static String resolveState(ActionSnapshot state) {
        if (state.deadOrDying()) {
            return DEATH;
        }
        if (state.riptide()) {
            return RIPTIDE;
        }
        if (state.sleeping()) {
            return SLEEP;
        }
        if (state.swimming()) {
            return SWIM;
        }
        if (state.swimmingPose() && state.moving()) {
            return CLIMB;
        }
        if (state.swimmingPose()) {
            return CLIMBING;
        }
        if (state.onClimbable()) {
            if (state.verticalSpeed() > LADDER_STILLNESS_SPEED) {
                return LADDER_UP;
            }
            if (state.verticalSpeed() < -LADDER_STILLNESS_SPEED) {
                return LADDER_DOWN;
            }
            return LADDER_STILLNESS;
        }
        if (state.flying()) {
            return FLY;
        }
        if (state.elytraFlying()) {
            return ELYTRA_FLY;
        }
        if (state.inWater() && !state.onGround()) {
            return SWIM_STAND;
        }
        if (state.attacked()) {
            return ATTACKED;
        }
        if (!state.onGround() && !state.inWater()) {
            return JUMP;
        }
        if (state.onGround() && state.crouching() && state.moving()) {
            return SNEAK;
        }
        if (state.onGround() && state.crouching()) {
            return SNEAKING;
        }
        if (state.onGround() && state.sprinting() && state.moving()) {
            return RUN;
        }
        if (state.onGround() && state.moving()) {
            return WALK;
        }
        return IDLE;
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

    public record ActionSnapshot(
            boolean deadOrDying,
            boolean riptide,
            boolean sleeping,
            boolean swimming,
            boolean swimmingPose,
            boolean onClimbable,
            boolean flying,
            boolean elytraFlying,
            boolean inWater,
            boolean onGround,
            boolean crouching,
            boolean sprinting,
            boolean attacked,
            boolean moving,
            float verticalSpeed
    ) {
    }
}
