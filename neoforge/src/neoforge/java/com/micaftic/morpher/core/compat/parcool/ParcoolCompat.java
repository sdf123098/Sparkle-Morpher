package com.micaftic.morpher.core.compat.parcool;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.geckolib3.core.controller.IAnimationController;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Optional;
import java.util.function.BiFunction;

/**
 * NeoForge ParCool compatibility facade.
 *
 * <p>The real bridge lives in the neoforge source set
 * ({@link com.micaftic.morpher.core.compat.parcool.ParcoolCompatImpl}); this
 * common class keeps the call sites (animation manager / resolver / bindings)
 * independent of the optional mod.</p>
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

    public static Optional<BiFunction<String, CustomPlayerEntity, IAnimationController<CustomPlayerEntity>>> getControllerFactory() {
        return ParcoolCompatImpl.getControllerFactory();
    }

    public static boolean isPlayerParcooling(Player player) {
        return ParcoolCompatImpl.isPlayerParcooling(player);
    }

    public static String getActionName(Player player) {
        return ParcoolCompatImpl.getActionName(player);
    }

    public static void registerBindings(CtrlBinding binding) {
        ParcoolCompatImpl.registerBindings(binding);
    }
}
