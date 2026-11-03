package com.micaftic.morpher.core.compat.sbackpack;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Optional;

public final class SBackpackCompat {

    private SBackpackCompat() {
    }

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void setupRenderLayers() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static Optional<Pair<String, String>> getInCompatibleInfo() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void registerControllerFunctions(CtrlBinding binding) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static ItemStack getBackpackItem(LivingEntity livingEntity) {
        throw new AssertionError();
    }
}
