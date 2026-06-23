package com.micaftic.morpher.core.api.item.fabric;

import com.micaftic.morpher.client.animation.condition.InnerClassify;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import com.micaftic.morpher.core.api.item.LanceActionState;
import com.micaftic.morpher.core.api.item.MaceActionState;
import com.micaftic.morpher.core.api.item.TridentActionState;
import com.micaftic.morpher.core.api.item.WeaponActionState;
import com.micaftic.morpher.core.api.item.WeaponKind;

public final class WeaponActionBridgeImpl {

    private static final float LANCE_LUNGE_SPEED = 0.35f;
    private static final float MACE_SMASH_FALL_DISTANCE = 1.5f;

    private WeaponActionBridgeImpl() {
    }

    public static WeaponActionState get(LivingEntity entity, float partialTick) {
        if (entity == null) {
            return WeaponActionState.EMPTY;
        }
        ItemStack stack = entity.getMainHandItem();
        WeaponKind kind = InnerClassify.getWeaponKind(stack);
        Vec3 movement = entity.getDeltaMovement();
        float speed = horizontalSpeed(movement);
        float verticalSpeed = (float) movement.y;
        return switch (kind) {
            case TRIDENT -> new WeaponActionState(kind, buildTridentState(entity, stack, partialTick), LanceActionState.EMPTY, MaceActionState.EMPTY, speed, verticalSpeed);
            case LANCE -> new WeaponActionState(kind, TridentActionState.EMPTY, buildLanceState(entity, stack, partialTick, speed), MaceActionState.EMPTY, speed, verticalSpeed);
            case MACE -> new WeaponActionState(kind, TridentActionState.EMPTY, LanceActionState.EMPTY, buildMaceState(entity, partialTick, verticalSpeed), speed, verticalSpeed);
            case NONE -> WeaponActionState.EMPTY;
        };
    }

    private static TridentActionState buildTridentState(LivingEntity entity, ItemStack stack, float partialTick) {
        boolean using = isUsingMainHand(entity) && stack.getUseAnimation() == UseAnim.SPEAR;
        boolean attacking = isAttackingMainHand(entity);
        return new TridentActionState(
                true,
                using,
                using,
                entity.isAutoSpinAttack(),
                attacking,
                getUseTicks(entity, using, partialTick),
                getAttackTicks(entity, attacking, partialTick));
    }

    private static LanceActionState buildLanceState(LivingEntity entity, ItemStack stack, float partialTick, float speed) {
        boolean using = isUsingMainHand(entity) && stack.getUseAnimation() == UseAnim.SPEAR;
        boolean attacking = isAttackingMainHand(entity);
        boolean riding = entity.isPassenger();
        boolean fallFlying = entity.isFallFlying();
        boolean lunging = speed >= LANCE_LUNGE_SPEED && (using || attacking || riding || fallFlying);
        boolean jabbing = attacking && !lunging;
        return new LanceActionState(
                true,
                using,
                using,
                jabbing,
                lunging,
                riding,
                riding && using,
                fallFlying,
                getUseTicks(entity, using, partialTick),
                getAttackTicks(entity, attacking, partialTick),
                speed,
                getChargeProgress(entity, stack, using, partialTick));
    }

    private static MaceActionState buildMaceState(LivingEntity entity, float partialTick, float verticalSpeed) {
        boolean attacking = isAttackingMainHand(entity);
        float fallDistance = entity.fallDistance;
        boolean falling = !entity.onGround() && verticalSpeed < -0.08f && fallDistance > 0.0f;
        boolean canSmash = falling && fallDistance >= MACE_SMASH_FALL_DISTANCE;
        boolean smashing = attacking && canSmash;
        boolean windBursting = false;
        return new MaceActionState(
                true,
                falling,
                canSmash,
                smashing,
                windBursting,
                attacking,
                entity.isPassenger(),
                entity.isFallFlying(),
                fallDistance,
                verticalSpeed,
                getAttackTicks(entity, attacking, partialTick),
                canSmash ? Math.min(1.0f, fallDistance / 3.0f) : 0.0f);
    }

    private static boolean isUsingMainHand(LivingEntity entity) {
        return entity.isUsingItem() && entity.getUseItemRemainingTicks() > 0 && entity.getUsedItemHand() == InteractionHand.MAIN_HAND;
    }

    private static boolean isAttackingMainHand(LivingEntity entity) {
        return entity.swinging && entity.swingingArm == InteractionHand.MAIN_HAND;
    }

    private static float getUseTicks(LivingEntity entity, boolean using, float partialTick) {
        return using ? Math.max(0.0f, entity.getTicksUsingItem() + partialTick) : 0.0f;
    }

    private static float getAttackTicks(LivingEntity entity, boolean attacking, float partialTick) {
        return attacking ? Math.max(0.0f, entity.swingTime + partialTick) : 0.0f;
    }

    private static float getChargeProgress(LivingEntity entity, ItemStack stack, boolean using, float partialTick) {
        if (!using) {
            return 0.0f;
        }
        int duration = stack.getUseDuration(entity);
        if (duration <= 0) {
            return 0.0f;
        }
        return Math.min(1.0f, getUseTicks(entity, true, partialTick) / duration);
    }

    private static float horizontalSpeed(Vec3 movement) {
        return (float) Math.sqrt(movement.x * movement.x + movement.z * movement.z);
    }
}
