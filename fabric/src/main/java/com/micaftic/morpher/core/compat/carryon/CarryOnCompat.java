package com.micaftic.morpher.core.compat.carryon;

import com.micaftic.morpher.client.core.HostControllerSlot;
import com.micaftic.morpher.client.core.HostMolangSink;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.function.BiFunction;

public final class CarryOnCompat {

    private CarryOnCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.carryon.fabric.CarryOnCompatImpl.isLoaded();
    }

    public static Optional<BiFunction<String, CustomPlayerEntity, HostControllerSlot<CustomPlayerEntity>>> getControllerFactory() {
        return com.micaftic.morpher.core.compat.carryon.fabric.CarryOnCompatImpl.getControllerFactory();
    }

    public static boolean isPlayerCarrying(Player player) {
        return com.micaftic.morpher.core.compat.carryon.fabric.CarryOnCompatImpl.isPlayerCarrying(player);
    }

    public static void registerBindings(HostMolangSink binding) {
        com.micaftic.morpher.core.compat.carryon.fabric.CarryOnCompatImpl.registerBindings(binding);
    }
}
