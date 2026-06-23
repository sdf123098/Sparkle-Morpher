package com.micaftic.morpher.core.compat.carryon.fabric;

import com.micaftic.morpher.core.compat.carryon.CarryOnDataHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class CarryOnDataHelperImpl {

    private CarryOnDataHelperImpl() {
    }

    public static boolean isPlayerCarrying(LivingEntity livingEntity) {
        return false;
    }

    public static CarryOnDataHelper.CarryType getCarryType(Player player) {
        return CarryOnDataHelper.CarryType.NONE;
    }
}
