package com.micaftic.morpher.core.compat.gun.swarfare.fabric;

import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class SWarfareCompatImpl {

    private SWarfareCompatImpl() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static boolean isGunItem(ItemStack itemStack) {
        return false;
    }

    public static boolean isPlayerAiming(Player player) {
        return false;
    }

    public static void applyGunTransform(ItemStack stack, AnimatedGeoModel model, LivingEntity entity, PoseStack poseStack, int packedLightIn, float partialTicks) {
    }

    public static PlayState handleTaczAnim(LivingEntity entity, AnimationEvent<? extends LivingAnimatable<? extends LivingEntity>> event, String str, ILoopType loopType) {
        return null;
    }

    public static PlayState handleGunHoldAnim(ItemStack stack, AnimationEvent<? extends LivingAnimatable<? extends LivingEntity>> event) {
        return null;
    }

    public static PlayState handleGunActionAnim(ItemStack stack, AnimationEvent<? extends LivingAnimatable<? extends LivingEntity>> event) {
        return null;
    }

    public static ResourceLocation getGunTexture(ItemStack stack) {
        return null;
    }
}
