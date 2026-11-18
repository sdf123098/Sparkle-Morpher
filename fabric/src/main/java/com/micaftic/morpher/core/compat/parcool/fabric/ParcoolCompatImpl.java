package com.micaftic.morpher.core.compat.parcool.fabric;

import com.micaftic.morpher.client.core.HostControllerSlot;
import com.micaftic.morpher.client.core.HostMolangSink;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import dev.spm.core.scene.MolangValue;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Optional;
import java.util.function.BiFunction;

public final class ParcoolCompatImpl {

    private ParcoolCompatImpl() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static Optional<Pair<String, String>> getInCompatibleInfo() {
        return Optional.empty();
    }

    public static Optional<BiFunction<String, CustomPlayerEntity, HostControllerSlot<CustomPlayerEntity>>> getControllerFactory() {
        return Optional.empty();
    }

    public static boolean isPlayerParcooling(Player player) {
        return false;
    }

    public static String getActionName(Player player) {
        return "";
    }

    public static void registerBindings(HostMolangSink binding) {
        binding.var("parcool_loaded", event -> MolangValue.bool(false));
        binding.var("parcool_doing", event -> MolangValue.bool(false));
        binding.var("parcool_state", event -> MolangValue.text(""));
    }
}
