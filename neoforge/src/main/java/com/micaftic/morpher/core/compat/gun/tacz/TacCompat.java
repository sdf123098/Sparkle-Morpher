package com.micaftic.morpher.core.compat.gun.tacz;

import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostMolangSink;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.render.ModelScenePose;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.spm.core.scene.CodedLoopMode;
import dev.spm.core.scene.MolangValue;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class TacCompat {

    private TacCompat() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static void registerControllerFunctions(HostMolangSink binding) {
        binding.var("tac_hold_gun", event -> MolangValue.bool(false));
        binding.var("tac_gun_type", event -> MolangValue.text(""));
        binding.var("tac_gun_id", event -> MolangValue.text(""));
        binding.var("tac_is_fire", event -> MolangValue.bool(false));
        binding.var("tac_is_aim", event -> MolangValue.bool(false));
        binding.var("tac_is_reload", event -> MolangValue.bool(false));
        binding.var("tac_is_melee", event -> MolangValue.bool(false));
        binding.var("tac_is_draw", event -> MolangValue.bool(false));
        binding.var("tac_fire_mode", event -> MolangValue.text(""));
    }

    public static void applyItemTransform(ItemStack stack, ModelScenePose model, LivingEntity entity, PoseStack poseStack, int packedLightIn, float partialTicks) {
    }

    public static @Nullable HostPlayState.Decision handleTaczAnimState(
            LivingEntity entity,
            HostActionEvent<? extends LivingAnimatable<?>> event,
            String animation,
            CodedLoopMode loopType) {
        return null;
    }

    public static @Nullable HostPlayState.Decision handleGunHoldAnimState(
            ItemStack stack,
            HostActionEvent<? extends LivingAnimatable<?>> event) {
        return null;
    }

    public static @Nullable HostPlayState.Decision handleGunActionAnimState(
            ItemStack stack,
            HostActionEvent<? extends LivingAnimatable<?>> event) {
        return null;
    }

    public static void handleGunSound(LivingEntity entity, ItemStack stack) {
    }

    public static void handleItemSound(ItemStack stack) {
    }

    public static Identifier getGunTexture(ItemStack stack) {
        return null;
    }
}
