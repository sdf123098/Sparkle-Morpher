package com.micaftic.morpher.core.compat.carryon;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.geckolib3.core.controller.IAnimationController;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.function.BiFunction;

public final class CarryOnCompat {

    private CarryOnCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.carryon.fabric.CarryOnCompatImpl.isLoaded();
    }

    public static Optional<BiFunction<String, CustomPlayerEntity, IAnimationController<CustomPlayerEntity>>> getControllerFactory() {
        return com.micaftic.morpher.core.compat.carryon.fabric.CarryOnCompatImpl.getControllerFactory();
    }

    public static boolean isPlayerCarrying(Player player) {
        return com.micaftic.morpher.core.compat.carryon.fabric.CarryOnCompatImpl.isPlayerCarrying(player);
    }

    public static void registerBindings(CtrlBinding binding) {
        com.micaftic.morpher.core.compat.carryon.fabric.CarryOnCompatImpl.registerBindings(binding);
    }
}
