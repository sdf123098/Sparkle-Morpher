package com.micaftic.morpher.core.compat.gun.swarfare.fabric;

import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.render.ModelScenePose;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.spm.core.scene.CodedLoopMode;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

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

    public static void applyGunTransform(ItemStack stack, ModelScenePose model, LivingEntity entity, PoseStack poseStack, int packedLightIn, float partialTicks) {
    }

    public static @Nullable HostPlayState.Decision handleTaczAnim(
            LivingEntity entity,
            HostActionEvent<? extends LivingAnimatable<? extends LivingEntity>> event,
            String animation,
            CodedLoopMode loopType) {
        return null;
    }

    public static @Nullable HostPlayState.Decision handleGunHoldAnim(
            ItemStack stack,
            HostActionEvent<? extends LivingAnimatable<? extends LivingEntity>> event) {
        return null;
    }

    public static @Nullable HostPlayState.Decision handleGunActionAnim(
            ItemStack stack,
            HostActionEvent<? extends LivingAnimatable<? extends LivingEntity>> event) {
        return null;
    }

    public static Identifier getGunTexture(ItemStack stack) {
        return null;
    }
}
