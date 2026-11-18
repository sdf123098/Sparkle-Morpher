package com.micaftic.morpher.core.compat.gun.swarfare;

import com.micaftic.morpher.client.entity.LivingAnimatable;
import dev.spm.core.scene.CodedLoopMode;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.render.ModelScenePose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class SWarfareCompat {

    private SWarfareCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.gun.swarfare.fabric.SWarfareCompatImpl.isLoaded();
    }

    public static boolean isGunItem(ItemStack itemStack) {
        return com.micaftic.morpher.core.compat.gun.swarfare.fabric.SWarfareCompatImpl.isGunItem(itemStack);
    }

    public static boolean isPlayerAiming(Player player) {
        return com.micaftic.morpher.core.compat.gun.swarfare.fabric.SWarfareCompatImpl.isPlayerAiming(player);
    }

    public static void applyGunTransform(ItemStack stack, ModelScenePose model, LivingEntity entity, PoseStack poseStack, int packedLightIn, float partialTicks) {
        com.micaftic.morpher.core.compat.gun.swarfare.fabric.SWarfareCompatImpl.applyGunTransform(stack, model, entity, poseStack, packedLightIn, partialTicks);
    }

    public static HostPlayState.Decision handleTaczAnim(LivingEntity entity, HostActionEvent<? extends LivingAnimatable<? extends LivingEntity>> event, String str, CodedLoopMode loopType) {
        return com.micaftic.morpher.core.compat.gun.swarfare.fabric.SWarfareCompatImpl.handleTaczAnim(entity, event, str, loopType);
    }

    public static HostPlayState.Decision handleGunHoldAnim(ItemStack stack, HostActionEvent<? extends LivingAnimatable<? extends LivingEntity>> event) {
        return com.micaftic.morpher.core.compat.gun.swarfare.fabric.SWarfareCompatImpl.handleGunHoldAnim(stack, event);
    }

    public static HostPlayState.Decision handleGunActionAnim(ItemStack stack, HostActionEvent<? extends LivingAnimatable<? extends LivingEntity>> event) {
        return com.micaftic.morpher.core.compat.gun.swarfare.fabric.SWarfareCompatImpl.handleGunActionAnim(stack, event);
    }

    public static ResourceLocation getGunTexture(ItemStack stack) {
        return com.micaftic.morpher.core.compat.gun.swarfare.fabric.SWarfareCompatImpl.getGunTexture(stack);
    }
}
