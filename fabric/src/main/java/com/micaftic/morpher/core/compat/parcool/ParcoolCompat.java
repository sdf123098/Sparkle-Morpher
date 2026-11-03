package com.micaftic.morpher.core.compat.parcool;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.geckolib3.core.controller.IAnimationController;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Optional;
import java.util.function.BiFunction;

public final class ParcoolCompat {

    private ParcoolCompat() {
    }

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static Optional<Pair<String, String>> getInCompatibleInfo() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static Optional<BiFunction<String, CustomPlayerEntity, IAnimationController<CustomPlayerEntity>>> getControllerFactory() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isPlayerParcooling(Player player) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static String getActionName(Player player) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void registerBindings(CtrlBinding binding) {
        throw new AssertionError();
    }
}
