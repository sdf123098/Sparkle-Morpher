package com.micaftic.morpher.core.compat.gun.tacz;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class TacCompat {

    private TacCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.gun.tacz.fabric.TacCompatImpl.isLoaded();
    }

    public static void registerControllerFunctions(CtrlBinding binding) {
        com.micaftic.morpher.core.compat.gun.tacz.fabric.TacCompatImpl.registerControllerFunctions(binding);
    }

    public static void applyItemTransform(ItemStack stack, AnimatedGeoModel model, LivingEntity entity, PoseStack poseStack, int packedLightIn, float partialTicks) {
        com.micaftic.morpher.core.compat.gun.tacz.fabric.TacCompatImpl.applyItemTransform(stack, model, entity, poseStack, packedLightIn, partialTicks);
    }

    public static PlayState handleTaczAnimState(LivingEntity entity, AnimationEvent<? extends LivingAnimatable<?>> event, String animation, ILoopType loopType) {
        return com.micaftic.morpher.core.compat.gun.tacz.fabric.TacCompatImpl.handleTaczAnimState(entity, event, animation, loopType);
    }

    public static PlayState handleGunHoldAnimState(ItemStack stack, AnimationEvent<? extends LivingAnimatable<?>> event) {
        return com.micaftic.morpher.core.compat.gun.tacz.fabric.TacCompatImpl.handleGunHoldAnimState(stack, event);
    }

    public static PlayState handleGunActionAnimState(ItemStack stack, AnimationEvent<? extends LivingAnimatable<?>> event) {
        return com.micaftic.morpher.core.compat.gun.tacz.fabric.TacCompatImpl.handleGunActionAnimState(stack, event);
    }

    public static void handleGunSound(LivingEntity entity, ItemStack stack) {
        com.micaftic.morpher.core.compat.gun.tacz.fabric.TacCompatImpl.handleGunSound(entity, stack);
    }

    public static void handleItemSound(ItemStack stack) {
        com.micaftic.morpher.core.compat.gun.tacz.fabric.TacCompatImpl.handleItemSound(stack);
    }

    public static Identifier getGunTexture(ItemStack stack) {
        return com.micaftic.morpher.core.compat.gun.tacz.fabric.TacCompatImpl.getGunTexture(stack);
    }
}
