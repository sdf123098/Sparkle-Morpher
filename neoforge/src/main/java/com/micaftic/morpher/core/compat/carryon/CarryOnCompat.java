package com.micaftic.morpher.core.compat.carryon;

import com.micaftic.morpher.client.core.HostControllerSlot;
import com.micaftic.morpher.client.core.HostMolangSink;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import dev.spm.core.scene.MolangValue;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.function.BiFunction;

public final class CarryOnCompat {

    private CarryOnCompat() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static Optional<BiFunction<String, CustomPlayerEntity, HostControllerSlot<CustomPlayerEntity>>> getControllerFactory() {
        return Optional.empty();
    }

    public static boolean isPlayerCarrying(Player player) {
        return false;
    }

    public static void registerBindings(HostMolangSink binding) {
        binding.var("carryon_type", event -> MolangValue.text(""));
        binding.var("carryon_is_princess", event -> MolangValue.bool(false));
    }
}
