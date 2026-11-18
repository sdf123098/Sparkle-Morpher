package com.micaftic.morpher.core.compat.gun.tacz;

import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostMolangSink;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.render.ModelScenePose;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.spm.core.scene.CodedLoopMode;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class TacCompat {

    private TacCompat() {
    }

    public static boolean ownsFirstPersonViewmodel(LivingEntity entity) {
        return com.micaftic.morpher.core.compat.gun.tacz.fabric.TacCompatImpl.ownsFirstPersonViewmodel(entity);
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.gun.tacz.fabric.TacCompatImpl.isLoaded();
    }

    public static void registerControllerFunctions(HostMolangSink binding) {
        com.micaftic.morpher.core.compat.gun.tacz.fabric.TacCompatImpl.registerControllerFunctions(binding);
    }

    public static void applyItemTransform(ItemStack stack, ModelScenePose model, LivingEntity entity, PoseStack poseStack, int packedLightIn, float partialTicks) {
        com.micaftic.morpher.core.compat.gun.tacz.fabric.TacCompatImpl.applyItemTransform(stack, model, entity, poseStack, packedLightIn, partialTicks);
    }

    public static @Nullable HostPlayState.Decision handleTaczAnimState(
            LivingEntity entity,
            HostActionEvent<? extends LivingAnimatable<?>> event,
            String animation,
            CodedLoopMode loopType) {
        return com.micaftic.morpher.core.compat.gun.tacz.fabric.TacCompatImpl.handleTaczAnimState(entity, event, animation, loopType);
    }

    public static @Nullable HostPlayState.Decision handleGunHoldAnimState(
            ItemStack stack,
            HostActionEvent<? extends LivingAnimatable<?>> event) {
        return com.micaftic.morpher.core.compat.gun.tacz.fabric.TacCompatImpl.handleGunHoldAnimState(stack, event);
    }

    public static @Nullable HostPlayState.Decision handleGunActionAnimState(
            ItemStack stack,
            HostActionEvent<? extends LivingAnimatable<?>> event) {
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
