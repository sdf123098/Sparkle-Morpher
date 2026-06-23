package com.micaftic.morpher.core.compat.gun.swarfare;

import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class SWarfareCompat {

    private SWarfareCompat() {
    }

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isGunItem(ItemStack itemStack) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isPlayerAiming(Player player) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void applyGunTransform(ItemStack stack, AnimatedGeoModel model, LivingEntity entity, PoseStack poseStack, int packedLightIn, float partialTicks) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static PlayState handleTaczAnim(LivingEntity entity, AnimationEvent<? extends LivingAnimatable<? extends LivingEntity>> event, String str, ILoopType loopType) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static PlayState handleGunHoldAnim(ItemStack stack, AnimationEvent<? extends LivingAnimatable<? extends LivingEntity>> event) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static PlayState handleGunActionAnim(ItemStack stack, AnimationEvent<? extends LivingAnimatable<? extends LivingEntity>> event) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static ResourceLocation getGunTexture(ItemStack stack) {
        throw new AssertionError();
    }
}
