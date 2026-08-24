package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.geckolib3.core.builder.Animation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;
import com.micaftic.morpher.core.api.item.LanceActionState;

/**
 * Shared helper for the lance/spear animation pipeline.
 */
final class LanceAnimationTiming {

    private LanceAnimationTiming() {
    }

    static String[] selectChargeNames(LanceActionState lance) {
        if (lance.ridingCharge()) {
            return new String[]{"lance_riding_charge", "lance_charge", "use_mainhand:lance"};
        }
        if (lance.fallFlying()) {
            return new String[]{"lance_fall_flying_charge", "lance_charge", "use_mainhand:lance"};
        }
        return new String[]{"lance_charge", "use_mainhand:lance"};
    }

    static float sampleKineticChargeAnimationTick(ItemStack itemStack, float useTicks, Animation animation) {
        float duration = kineticChargeDurationTicks(itemStack);
        if (duration <= 0.0f || animation == null || animation.animationLength <= 0.0f) {
            return -1.0f;
        }
        return clamp01(useTicks / duration) * animation.animationLength;
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
