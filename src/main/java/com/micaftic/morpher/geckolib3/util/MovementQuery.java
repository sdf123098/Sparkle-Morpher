package com.micaftic.morpher.geckolib3.util;

import com.micaftic.morpher.client.entity.PlayerEntityFrameState;
import com.micaftic.morpher.geckolib3.core.EntityFrameStateTracker;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class MovementQuery {

    public static final float EPSILON = 1.0E-4f;

    private MovementQuery() {
    }

    public static float getTimeDelta(EntityFrameStateTracker<?> tracker) {
        float timeDelta = tracker.getTimeDelta();
        return Float.isFinite(timeDelta) && timeDelta > EPSILON ? timeDelta : 0.0f;
    }

    public static float getTimeDeltaSeconds(EntityFrameStateTracker<?> tracker) {
        return getTimeDelta(tracker) / 20.0f;
    }

    public static Vec3 getPositionDelta(Entity entity, EntityFrameStateTracker<?> tracker) {
        Vec3 trackerDelta = sanitize(tracker.getPositionDelta());
        if (hasMovement(trackerDelta)) {
            return trackerDelta;
        }

        if (shouldSuppressSyntheticWalk(tracker)) {
            return Vec3.ZERO;
        }

        Vec3 tickDelta = sanitize(new Vec3(entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo));
        if (hasMovement(tickDelta)) {
            return tickDelta;
        }

        Vec3 velocity = sanitize(entity.getDeltaMovement());
        if (hasMovement(velocity)) {
            float timeDelta = getTimeDelta(tracker);
            return velocity.scale(timeDelta > EPSILON ? timeDelta : 1.0f);
        }
        return Vec3.ZERO;
    }

    public static float getGroundSpeed(Entity entity, EntityFrameStateTracker<?> tracker, @Nullable AnimationEvent<?> event) {
        Vec3 trackerDelta = sanitize(tracker.getPositionDelta());
        float trackerSpeed = getHorizontalSpeedFromDelta(trackerDelta, tracker);
        if (isUsable(trackerSpeed)) {
            return trackerSpeed;
        }

        boolean suppressSyntheticWalk = shouldSuppressSyntheticWalk(tracker);
        if (suppressSyntheticWalk) {
            return 0.0f;
        }

        if (event != null) {
            float limbSwingAmount = Math.abs(event.getLimbSwingAmount());
            if (isUsable(limbSwingAmount)) {
                return limbSwingAmount;
            }
        }

        if (entity instanceof LivingEntity livingEntity) {
            float partialTick = event != null ? event.getPartialTick() : 1.0f;
            float walkSpeed = Math.abs(livingEntity.walkAnimation.speed(partialTick));
            if (isUsable(walkSpeed)) {
                return walkSpeed;
            }
        }

        Vec3 deltaMovement = sanitize(entity.getDeltaMovement());
        float velocitySpeed = 20.0f * horizontalLength(deltaMovement);
        if (isUsable(velocitySpeed)) {
            return velocitySpeed;
        }

        Vec3 tickDelta = sanitize(new Vec3(entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo));
        float tickSpeed = 20.0f * horizontalLength(tickDelta);
        return Float.isFinite(tickSpeed) && tickSpeed > 0.0f ? tickSpeed : 0.0f;
    }

    public static float getPhysicalGroundSpeed(Entity entity, EntityFrameStateTracker<?> tracker) {
        Vec3 trackerDelta = sanitize(tracker.getPositionDelta());
        float trackerSpeed = getHorizontalSpeedFromDelta(trackerDelta, tracker);
        if (isUsable(trackerSpeed)) {
            return trackerSpeed;
        }

        if (shouldSuppressSyntheticWalk(tracker)) {
            return 0.0f;
        }

        Vec3 deltaMovement = sanitize(entity.getDeltaMovement());
        float velocitySpeed = 20.0f * horizontalLength(deltaMovement);
        if (isUsable(velocitySpeed)) {
            return velocitySpeed;
        }

        Vec3 tickDelta = sanitize(new Vec3(entity.getX() - entity.xo, 0.0d, entity.getZ() - entity.zo));
        float tickSpeed = 20.0f * horizontalLength(tickDelta);
        return Float.isFinite(tickSpeed) && tickSpeed > 0.0f ? tickSpeed : 0.0f;
    }

    public static float getVerticalSpeed(Entity entity, EntityFrameStateTracker<?> tracker) {
        Vec3 trackerDelta = sanitize(tracker.getPositionDelta());
        float timeDelta = getTimeDelta(tracker);
        if (timeDelta > EPSILON && Math.abs(trackerDelta.y) > EPSILON) {
            float trackerSpeed = (20.0f * (float) trackerDelta.y) / timeDelta;
            if (Float.isFinite(trackerSpeed)) {
                return trackerSpeed;
            }
        }

        Vec3 deltaMovement = sanitize(entity.getDeltaMovement());
        if (Math.abs(deltaMovement.y) > EPSILON) {
            float velocitySpeed = 20.0f * (float) deltaMovement.y;
            if (Float.isFinite(velocitySpeed)) {
                return velocitySpeed;
            }
        }

        float tickSpeed = 20.0f * (float) (entity.getY() - entity.yo);
        return Float.isFinite(tickSpeed) ? tickSpeed : 0.0f;
    }

    public static float getDeltaMovementLength(Entity entity, EntityFrameStateTracker<?> tracker) {
        if (shouldSuppressSyntheticWalk(tracker)) {
            Vec3 trackerDelta = sanitize(tracker.getPositionDelta());
            return hasMovement(trackerDelta) ? (float) trackerDelta.length() : 0.0f;
        }

        Vec3 deltaMovement = sanitize(entity.getDeltaMovement());
        if (hasMovement(deltaMovement)) {
            return (float) deltaMovement.length();
        }
        return (float) getPositionDelta(entity, tracker).length();
    }

    private static float getHorizontalSpeedFromDelta(Vec3 delta, EntityFrameStateTracker<?> tracker) {
        float timeDelta = getTimeDelta(tracker);
        if (timeDelta <= EPSILON) {
            return 0.0f;
        }
        return (20.0f * horizontalLength(delta)) / timeDelta;
    }

    private static float horizontalLength(Vec3 vec3) {
        return Mth.sqrt((float) ((vec3.x * vec3.x) + (vec3.z * vec3.z)));
    }

    private static boolean hasMovement(Vec3 vec3) {
        return vec3.lengthSqr() > EPSILON * EPSILON;
    }

    private static boolean isUsable(float value) {
        return Float.isFinite(value) && value > EPSILON;
    }

    private static boolean shouldSuppressSyntheticWalk(EntityFrameStateTracker<?> tracker) {
        return tracker instanceof PlayerEntityFrameState playerState
                && !playerState.isLocalPlayer();
    }

    private static Vec3 sanitize(Vec3 vec3) {
        return Double.isFinite(vec3.x) && Double.isFinite(vec3.y) && Double.isFinite(vec3.z) ? vec3 : Vec3.ZERO;
    }
}
