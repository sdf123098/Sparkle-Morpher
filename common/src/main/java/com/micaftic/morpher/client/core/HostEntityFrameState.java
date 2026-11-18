package com.micaftic.morpher.client.core;

import com.micaftic.morpher.client.entity.PlayerEntityFrameState;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class HostEntityFrameState<T extends Entity> {

    public static final float EPSILON = 1.0E-4f;

    public T entity;
    private int currentTick;
    private Vec3 lastPosition;
    private String cachedControllerState;
    public float currentTime;
    public float timeDelta;
    private Vec3 positionDelta = Vec3.ZERO;
    private final IntOpenHashSet animatedEntities = new IntOpenHashSet();

    public HostEntityFrameState(T entity) {
        this.entity = entity;
    }

    public void reset() {
        this.animatedEntities.clear();
        this.currentTick = 0;
        this.lastPosition = null;
        this.positionDelta = Vec3.ZERO;
        this.cachedControllerState = null;
        this.currentTime = 0.0f;
        this.timeDelta = 0.0f;
    }

    public final void updateState(int tickCount, float seekTime, float frameTime) {
        if (this.currentTick < tickCount) {
            onTickUpdate(tickCount, this.currentTick);
            this.currentTick = tickCount;
        }
        if (this.currentTime < seekTime) {
            onTimeUpdate(seekTime, this.currentTime, frameTime);
            this.currentTime = seekTime;
        }
    }

    public void setEntity(T entity) {
        this.entity = entity;
    }

    public void onTimeUpdate(float newTime, float oldTime, float frameTime) {
        this.timeDelta = newTime - oldTime;
        updatePosition(frameTime);
        this.cachedControllerState = null;
    }

    public void onTickUpdate(int newTick, int oldTick) {
        this.animatedEntities.clear();
    }

    private void updatePosition(float partialTick) {
        Vec3 vec3 = new Vec3(
                Mth.lerp(partialTick, this.entity.xo, this.entity.getX()),
                Mth.lerp(partialTick, this.entity.yo, this.entity.getY()),
                Mth.lerp(partialTick, this.entity.zo, this.entity.getZ()));
        if (this.lastPosition != null) {
            this.positionDelta = vec3.subtract(this.lastPosition);
        }
        this.lastPosition = vec3;
    }

    public boolean markProcessed(int marker) {
        return this.animatedEntities.add(marker);
    }

    public boolean isProcessed(int marker) {
        return this.animatedEntities.contains(marker);
    }

    public Vec3 getPositionDelta() {
        return this.positionDelta;
    }

    public float getTimeDelta() {
        return this.timeDelta;
    }

    @Nullable
    public String getCachedControllerState() {
        return this.cachedControllerState;
    }

    public void setCachedControllerState(@Nullable String state) {
        this.cachedControllerState = state;
    }

    public float getNormalizedTimeDelta() {
        return Float.isFinite(this.timeDelta) && this.timeDelta > EPSILON ? this.timeDelta : 0.0f;
    }

    public float getTimeDeltaSeconds() {
        return getNormalizedTimeDelta() / 20.0f;
    }

    public Vec3 getResolvedPositionDelta(Entity entity) {
        Vec3 trackerDelta = sanitize(this.positionDelta);
        if (hasMovement(trackerDelta)) {
            return trackerDelta;
        }
        if (shouldSuppressSyntheticWalk()) {
            return Vec3.ZERO;
        }
        Vec3 tickDelta = sanitize(new Vec3(entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo));
        if (hasMovement(tickDelta)) {
            return tickDelta;
        }
        Vec3 velocity = sanitize(entity.getDeltaMovement());
        if (hasMovement(velocity)) {
            float dt = getNormalizedTimeDelta();
            return velocity.scale(dt > EPSILON ? dt : 1.0f);
        }
        return Vec3.ZERO;
    }

    public float getGroundSpeed(Entity entity, @Nullable HostActionEvent<?> event) {
        Vec3 trackerDelta = sanitize(this.positionDelta);
        float trackerSpeed = getHorizontalSpeedFromDelta(trackerDelta);
        if (isUsable(trackerSpeed)) {
            return trackerSpeed;
        }
        if (shouldSuppressSyntheticWalk()) {
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

    public float getPhysicalGroundSpeed(Entity entity) {
        Vec3 trackerDelta = sanitize(this.positionDelta);
        float trackerSpeed = getHorizontalSpeedFromDelta(trackerDelta);
        if (isUsable(trackerSpeed)) {
            return trackerSpeed;
        }
        if (shouldSuppressSyntheticWalk()) {
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

    public float getVerticalSpeed(Entity entity) {
        Vec3 trackerDelta = sanitize(this.positionDelta);
        float dt = getNormalizedTimeDelta();
        if (dt > EPSILON && Math.abs(trackerDelta.y) > EPSILON) {
            float trackerSpeed = (20.0f * (float) trackerDelta.y) / dt;
            if (Float.isFinite(trackerSpeed)) {
                return trackerSpeed;
            }
        }
        Vec3 deltaMovement = sanitize(entity.getDeltaMovement());
        if (Math.abs(deltaMovement.y) > 0.1) {
            float velocitySpeed = 20.0f * (float) deltaMovement.y;
            if (Float.isFinite(velocitySpeed)) {
                return velocitySpeed;
            }
        }
        float tickSpeed = 20.0f * (float) (entity.getY() - entity.yo);
        return Float.isFinite(tickSpeed) ? tickSpeed : 0.0f;
    }

    public float getDeltaMovementLength(Entity entity) {
        if (shouldSuppressSyntheticWalk()) {
            Vec3 trackerDelta = sanitize(this.positionDelta);
            return hasMovement(trackerDelta) ? (float) trackerDelta.length() : 0.0f;
        }
        Vec3 deltaMovement = sanitize(entity.getDeltaMovement());
        if (hasMovement(deltaMovement)) {
            return (float) deltaMovement.length();
        }
        return (float) getResolvedPositionDelta(entity).length();
    }

    private float getHorizontalSpeedFromDelta(Vec3 delta) {
        float dt = getNormalizedTimeDelta();
        if (dt <= EPSILON) {
            return 0.0f;
        }
        return (20.0f * horizontalLength(delta)) / dt;
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

    private boolean shouldSuppressSyntheticWalk() {
        return this instanceof PlayerEntityFrameState playerState && !playerState.isLocalPlayer();
    }

    private static Vec3 sanitize(Vec3 vec3) {
        return Double.isFinite(vec3.x) && Double.isFinite(vec3.y) && Double.isFinite(vec3.z) ? vec3 : Vec3.ZERO;
    }
}
