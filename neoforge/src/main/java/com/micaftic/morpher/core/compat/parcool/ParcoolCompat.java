package com.micaftic.morpher.core.compat.parcool;

import com.micaftic.morpher.client.core.HostControllerSlot;
import com.micaftic.morpher.client.core.HostMolangSink;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Optional;
import java.util.function.BiFunction;

/**
 * NeoForge ParCool compatibility facade.
 */
public final class ParcoolCompat {

    private ParcoolCompat() {
    }

    public static boolean isLoaded() {
        return ParcoolCompatImpl.isLoaded();
    }

    public static Optional<Pair<String, String>> getInCompatibleInfo() {
        return ParcoolCompatImpl.getInCompatibleInfo();
    }

    public static Optional<BiFunction<String, CustomPlayerEntity, HostControllerSlot<CustomPlayerEntity>>> getControllerFactory() {
        return ParcoolCompatImpl.getControllerFactory();
    }

    public static boolean isPlayerParcooling(Player player) {
        return ParcoolCompatImpl.isPlayerParcooling(player);
    }

    public static String getActionName(Player player) {
        return ParcoolCompatImpl.getActionName(player);
    }

    public static void registerBindings(HostMolangSink binding) {
        ParcoolCompatImpl.registerBindings(binding);
    }
}
