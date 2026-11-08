package com.micaftic.morpher.core.compat.swem;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import net.minecraft.world.entity.LivingEntity;

public final class SWEMCompat {

    private SWEMCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.swem.fabric.SWEMCompatImpl.isLoaded();
    }

    public static String getHorseGaitName(LivingEntity livingEntity) {
        return com.micaftic.morpher.core.compat.swem.fabric.SWEMCompatImpl.getHorseGaitName(livingEntity);
    }

    public static void registerControllerFunctions(CtrlBinding ctrlBinding) {
        com.micaftic.morpher.core.compat.swem.fabric.SWEMCompatImpl.registerControllerFunctions(ctrlBinding);
    }
}
