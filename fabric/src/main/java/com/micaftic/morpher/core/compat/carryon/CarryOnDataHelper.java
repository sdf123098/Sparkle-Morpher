package com.micaftic.morpher.core.compat.carryon;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class CarryOnDataHelper {

    public enum CarryType {
        ENTITY,
        BLOCK,
        PLAYER,
        NONE
    }

    private CarryOnDataHelper() {
    }

    @ExpectPlatform
    public static boolean isPlayerCarrying(LivingEntity livingEntity) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static CarryType getCarryType(Player player) {
        throw new AssertionError();
    }
}
