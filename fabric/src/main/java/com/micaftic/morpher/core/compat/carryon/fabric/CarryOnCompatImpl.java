package com.micaftic.morpher.core.compat.carryon.fabric;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.geckolib3.core.controller.IAnimationController;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.function.BiFunction;

public final class CarryOnCompatImpl {

    private CarryOnCompatImpl() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static Optional<BiFunction<String, CustomPlayerEntity, IAnimationController<CustomPlayerEntity>>> getControllerFactory() {
        return Optional.empty();
    }

    public static boolean isPlayerCarrying(Player player) {
        return false;
    }

    public static void registerBindings(CtrlBinding binding) {
        binding.livingEntityVar("carryon_type", ctx -> StringPool.EMPTY);
        binding.livingEntityVar("carryon_is_princess", ctx -> false);
    }
}
