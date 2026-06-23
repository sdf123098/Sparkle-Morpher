package com.micaftic.morpher.core.compat.carryon;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.geckolib3.core.controller.IAnimationController;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.function.BiFunction;

public final class CarryOnCompat {

    private CarryOnCompat() {
    }

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static Optional<BiFunction<String, CustomPlayerEntity, IAnimationController<CustomPlayerEntity>>> getControllerFactory() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isPlayerCarrying(Player player) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void registerBindings(CtrlBinding binding) {
        throw new AssertionError();
    }
}
