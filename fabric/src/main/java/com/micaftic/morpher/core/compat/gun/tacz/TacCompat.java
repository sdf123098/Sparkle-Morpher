package com.micaftic.morpher.core.compat.gun.tacz;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class TacCompat {

    private TacCompat() {
    }

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void registerControllerFunctions(CtrlBinding binding) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void applyItemTransform(ItemStack stack, AnimatedGeoModel model, LivingEntity entity, PoseStack poseStack, int packedLightIn, float partialTicks) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static PlayState handleTaczAnimState(LivingEntity entity, AnimationEvent<? extends LivingAnimatable<?>> event, String animation, ILoopType loopType) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static PlayState handleGunHoldAnimState(ItemStack stack, AnimationEvent<? extends LivingAnimatable<?>> event) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static PlayState handleGunActionAnimState(ItemStack stack, AnimationEvent<? extends LivingAnimatable<?>> event) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void handleGunSound(LivingEntity entity, ItemStack stack) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void handleItemSound(ItemStack stack) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static ResourceLocation getGunTexture(ItemStack stack) {
        throw new AssertionError();
    }
}
