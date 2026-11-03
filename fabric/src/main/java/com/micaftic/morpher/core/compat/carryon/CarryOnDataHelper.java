package com.micaftic.morpher.core.compat.carryon;

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

    public static boolean isPlayerCarrying(LivingEntity livingEntity) {
        return com.micaftic.morpher.core.compat.carryon.fabric.CarryOnDataHelperImpl.isPlayerCarrying(livingEntity);
    }

    public static CarryType getCarryType(Player player) {
        return com.micaftic.morpher.core.compat.carryon.fabric.CarryOnDataHelperImpl.getCarryType(player);
    }
}
