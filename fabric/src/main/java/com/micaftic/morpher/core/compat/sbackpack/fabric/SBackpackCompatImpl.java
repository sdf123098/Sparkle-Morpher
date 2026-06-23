package com.micaftic.morpher.core.compat.sbackpack.fabric;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Optional;

public final class SBackpackCompatImpl {

    private SBackpackCompatImpl() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static void setupRenderLayers() {
    }

    public static Optional<Pair<String, String>> getInCompatibleInfo() {
        return Optional.empty();
    }

    public static void registerControllerFunctions(CtrlBinding binding) {
        binding.livingEntityVar("has_sophisticated_backpack", ctx -> false);
    }

    public static ItemStack getBackpackItem(LivingEntity livingEntity) {
        return ItemStack.EMPTY;
    }
}
