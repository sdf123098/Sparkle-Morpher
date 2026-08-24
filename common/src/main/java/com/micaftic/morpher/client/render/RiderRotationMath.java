package com.micaftic.morpher.client.render;

/**
 * Pure rotation math shared by player render-state handling and living-vehicle constraints.
 */
public final class RiderRotationMath {

    private RiderRotationMath() {
    }

    /**
     * Render state stores head yaw relative to the rendered body. Rebuild the absolute yaw
     * before applying vanilla's living-vehicle body constraint.
     */
    public static float absoluteHeadYaw(float bodyYaw, float relativeHeadYaw) {
        return bodyYaw + relativeHeadYaw;
    }

    /** Applies the vanilla living-vehicle yaw clamp to an absolute head yaw. */
    public static LivingVehicleRotation constrainToLivingVehicle(float absoluteHeadYaw, float vehicleBodyYaw) {
        float clampedHeadDelta = clamp(wrapDegrees(absoluteHeadYaw - vehicleBodyYaw), -85.0f, 85.0f);
        float bodyYaw = absoluteHeadYaw - clampedHeadDelta;
        if (clampedHeadDelta * clampedHeadDelta > 2500.0f) {
            bodyYaw += clampedHeadDelta * 0.2f;
        }
        return new LivingVehicleRotation(bodyYaw, absoluteHeadYaw - bodyYaw);
    }

    public record LivingVehicleRotation(float bodyYaw, float relativeHeadYaw) {
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }
        if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
