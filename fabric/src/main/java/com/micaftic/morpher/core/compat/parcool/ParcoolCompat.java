package com.micaftic.morpher.core.compat.parcool;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.geckolib3.core.controller.IAnimationController;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Optional;
import java.util.function.BiFunction;

public final class ParcoolCompat {

    private ParcoolCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.parcool.fabric.ParcoolCompatImpl.isLoaded();
    }

    public static Optional<Pair<String, String>> getInCompatibleInfo() {
        return com.micaftic.morpher.core.compat.parcool.fabric.ParcoolCompatImpl.getInCompatibleInfo();
    }

    public static Optional<BiFunction<String, CustomPlayerEntity, IAnimationController<CustomPlayerEntity>>> getControllerFactory() {
        return com.micaftic.morpher.core.compat.parcool.fabric.ParcoolCompatImpl.getControllerFactory();
    }

    public static boolean isPlayerParcooling(Player player) {
        return com.micaftic.morpher.core.compat.parcool.fabric.ParcoolCompatImpl.isPlayerParcooling(player);
    }

    public static String getActionName(Player player) {
        return com.micaftic.morpher.core.compat.parcool.fabric.ParcoolCompatImpl.getActionName(player);
    }

    public static void registerBindings(CtrlBinding binding) {
        com.micaftic.morpher.core.compat.parcool.fabric.ParcoolCompatImpl.registerBindings(binding);
    }
}
