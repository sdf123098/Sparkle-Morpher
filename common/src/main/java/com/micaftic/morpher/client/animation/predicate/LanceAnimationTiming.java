package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.core.api.item.LanceActionState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;

/**
 * Shared helper for the lance/spear animation pipeline.
 */
final class LanceAnimationTiming {

    private LanceAnimationTiming() {
    }

    static String[] selectChargeNames(LanceActionState lance) {
        return selectChargeNames(lance, HumanoidArm.RIGHT);
    }

    static String[] selectChargeNames(LanceActionState lance, HumanoidArm usedArm) {
        if (usedArm == HumanoidArm.LEFT) {
            return new String[]{"use_offhand:lance"};
        }
        if (lance.ridingCharge()) {
            return new String[]{"lance_riding_charge", "lance_charge", "use_mainhand:lance"};
        }
        if (lance.fallFlying()) {
            return new String[]{"lance_fall_flying_charge", "lance_charge", "use_mainhand:lance"};
        }
        return new String[]{"lance_charge", "use_mainhand:lance"};
    }

    private static final double BUILTIN_LANCE_MIN_LENGTH_SECONDS = 12.5;
    private static final double BUILTIN_LANCE_RAISE_END_SECONDS = 1.0 / 3.0;
    private static final double BUILTIN_LANCE_ENGAGED_END_SECONDS = 4.0;
    private static final double BUILTIN_LANCE_TIRED_END_SECONDS = 8.0;
    private static final double BUILTIN_LANCE_LOWER_END_SECONDS = 12.0;
    private static final float EXHAUSTED_DROP_TICKS = 10.0f;
    private static final double MIN_PLAYBACK_SPEED = 0.05;
    private static final double MAX_PLAYBACK_SPEED = 6.0;
    private static final double SPEED_CORRECTION_GAIN = 4.0;

    static double computeChargePlaybackSpeed(
            ItemStack itemStack,
            float useTicks,
            ClipSpec animation,
            double currentAnimTimeSeconds) {
        if (itemStack == null || itemStack.isEmpty() || animation == null) {
            return 1.0;
        }
        double clipLengthSeconds = animation.lengthSeconds();
        if (!Double.isFinite(clipLengthSeconds) || clipLengthSeconds <= 0.0) {
            return 1.0;
        }
        KineticWeapon kineticWeapon = itemStack.get(DataComponents.KINETIC_WEAPON);
        if (kineticWeapon == null) {
            return 1.0;
        }
        int delayTicks = Math.max(1, kineticWeapon.delayTicks());
        int damageEndTicks = Math.max(delayTicks + 1, kineticWeapon.computeDamageUseDuration());
        int dismountEndTicks = kineticWeapon.dismountConditions()
                .map(KineticWeapon.Condition::maxDurationTicks)
                .map(d -> delayTicks + Math.max(1, d))
                .orElse(delayTicks + Math.max(1, (damageEndTicks - delayTicks) / 3));
        int knockbackEndTicks = kineticWeapon.knockbackConditions()
                .map(KineticWeapon.Condition::maxDurationTicks)
                .map(d -> delayTicks + Math.max(1, d))
                .orElse(delayTicks + Math.max(2, (damageEndTicks - delayTicks) * 2 / 3));
        return computeStageSyncedSpeed(
                useTicks,
                delayTicks,
                dismountEndTicks,
                knockbackEndTicks,
                damageEndTicks,
                clipLengthSeconds,
                currentAnimTimeSeconds);
    }

    static double computeStageSyncedSpeed(
            float useTicks,
            int delayTicks,
            int dismountEndTicks,
            int knockbackEndTicks,
            int damageEndTicks,
            double clipLengthSeconds,
            double currentAnimTimeSeconds) {
        if (!Double.isFinite(clipLengthSeconds) || clipLengthSeconds <= 0.0 || damageEndTicks <= 0) {
            return 1.0;
        }
        double safeUseTicks = Float.isFinite(useTicks) ? Math.max(0.0, useTicks) : 0.0;
        double safeCurrent = Double.isFinite(currentAnimTimeSeconds) ? Math.max(0.0, currentAnimTimeSeconds) : 0.0;
        double targetSeconds = computeTargetChargeSeconds(
                safeUseTicks, delayTicks, dismountEndTicks, knockbackEndTicks, damageEndTicks, clipLengthSeconds);
        double nominalRate = computeNominalChargeRate(
                safeUseTicks, delayTicks, dismountEndTicks, knockbackEndTicks, damageEndTicks, clipLengthSeconds);
        if (safeUseTicks >= damageEndTicks + EXHAUSTED_DROP_TICKS && safeCurrent >= clipLengthSeconds - 0.02) {
            return 1.0;
        }
        double corrected = nominalRate + (targetSeconds - safeCurrent) * SPEED_CORRECTION_GAIN;
        return Math.max(MIN_PLAYBACK_SPEED, Math.min(MAX_PLAYBACK_SPEED, corrected));
    }

    static double computeTargetChargeSeconds(
            double useTicks,
            int delayTicks,
            int dismountEndTicks,
            int knockbackEndTicks,
            int damageEndTicks,
            double clipLengthSeconds) {
        double t0 = Math.max(1.0, delayTicks);
        double t3 = Math.max(t0 + 3.0, damageEndTicks);
        double t4 = t3 + EXHAUSTED_DROP_TICKS;
        if (clipLengthSeconds < BUILTIN_LANCE_MIN_LENGTH_SECONDS) {
            return clamp01((float) (useTicks / t4)) * clipLengthSeconds;
        }
        double t1 = Math.max(t0 + 1.0, Math.min(t3 - 2.0, dismountEndTicks));
        double t2 = Math.max(t1 + 1.0, Math.min(t3 - 1.0, knockbackEndTicks));
        if (useTicks <= t0) {
            return lerp(0.0, BUILTIN_LANCE_RAISE_END_SECONDS, useTicks / t0);
        }
        if (useTicks <= t1) {
            return lerp(BUILTIN_LANCE_RAISE_END_SECONDS, BUILTIN_LANCE_ENGAGED_END_SECONDS, (useTicks - t0) / (t1 - t0));
        }
        if (useTicks <= t2) {
            return lerp(BUILTIN_LANCE_ENGAGED_END_SECONDS, BUILTIN_LANCE_TIRED_END_SECONDS, (useTicks - t1) / (t2 - t1));
        }
        if (useTicks <= t3) {
            return lerp(BUILTIN_LANCE_TIRED_END_SECONDS, BUILTIN_LANCE_LOWER_END_SECONDS, (useTicks - t2) / (t3 - t2));
        }
        if (useTicks <= t4) {
            return lerp(BUILTIN_LANCE_LOWER_END_SECONDS, clipLengthSeconds, (useTicks - t3) / (t4 - t3));
        }
        return clipLengthSeconds;
    }

    private static double computeNominalChargeRate(
            double useTicks,
            int delayTicks,
            int dismountEndTicks,
            int knockbackEndTicks,
            int damageEndTicks,
            double clipLengthSeconds) {
        double t0 = Math.max(1.0, delayTicks);
        double t3 = Math.max(t0 + 3.0, damageEndTicks);
        double t4 = t3 + EXHAUSTED_DROP_TICKS;
        if (clipLengthSeconds < BUILTIN_LANCE_MIN_LENGTH_SECONDS) {
            return useTicks <= t4 ? clipLengthSeconds / (t4 / 20.0) : 1.0;
        }
        double t1 = Math.max(t0 + 1.0, Math.min(t3 - 2.0, dismountEndTicks));
        double t2 = Math.max(t1 + 1.0, Math.min(t3 - 1.0, knockbackEndTicks));
        if (useTicks <= t0) {
            return BUILTIN_LANCE_RAISE_END_SECONDS / (t0 / 20.0);
        }
        if (useTicks <= t1) {
            return (BUILTIN_LANCE_ENGAGED_END_SECONDS - BUILTIN_LANCE_RAISE_END_SECONDS) / ((t1 - t0) / 20.0);
        }
        if (useTicks <= t2) {
            return (BUILTIN_LANCE_TIRED_END_SECONDS - BUILTIN_LANCE_ENGAGED_END_SECONDS) / ((t2 - t1) / 20.0);
        }
        if (useTicks <= t3) {
            return (BUILTIN_LANCE_LOWER_END_SECONDS - BUILTIN_LANCE_TIRED_END_SECONDS) / ((t3 - t2) / 20.0);
        }
        if (useTicks <= t4) {
            return (clipLengthSeconds - BUILTIN_LANCE_LOWER_END_SECONDS) / (EXHAUSTED_DROP_TICKS / 20.0);
        }
        return 1.0;
    }

    private static double lerp(double start, double end, double alpha) {
        double clamped = Math.max(0.0, Math.min(1.0, alpha));
        return start + (end - start) * clamped;
    }

    static float sampleKineticChargeAnimationTick(ItemStack itemStack, float useTicks, ClipSpec animation) {
        float duration = kineticChargeDurationTicks(itemStack);
        double lengthTicks = animation != null ? animation.lengthSeconds() * 20.0 : 0.0;
        if (duration <= 0.0f || lengthTicks <= 0.0) {
            return -1.0f;
        }
        return clamp01(useTicks / duration) * (float) lengthTicks;
    }

    static float kineticChargeProgress(ItemStack itemStack, float useTicks) {
        float duration = kineticChargeDurationTicks(itemStack);
        if (duration <= 0.0f) {
            return 0.0f;
        }
        return clamp01(useTicks / duration);
    }

    static float kineticChargeDurationTicks(ItemStack itemStack) {
        KineticWeapon kineticWeapon = itemStack.get(DataComponents.KINETIC_WEAPON);
        if (kineticWeapon == null) {
            return 0.0f;
        }
        return Math.max(1.0f, kineticWeapon.computeDamageUseDuration());
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
