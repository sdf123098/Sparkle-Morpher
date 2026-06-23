package com.micaftic.morpher.core.compat.sbackpack;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Optional;

public final class SBackpackCompat {

    private SBackpackCompat() {
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
    }

    public static ItemStack getBackpackItem(LivingEntity livingEntity) {
        return ItemStack.EMPTY;
    }
}
