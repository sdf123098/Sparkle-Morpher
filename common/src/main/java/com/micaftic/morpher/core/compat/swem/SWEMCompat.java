package com.micaftic.morpher.core.compat.swem;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import net.minecraft.world.entity.LivingEntity;

public final class SWEMCompat {

    private SWEMCompat() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static String getHorseGaitName(LivingEntity livingEntity) {
        return "";
    }

    public static void registerControllerFunctions(CtrlBinding ctrlBinding) {
    }
}
