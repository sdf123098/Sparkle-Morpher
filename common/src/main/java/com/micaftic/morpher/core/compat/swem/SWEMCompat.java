package com.micaftic.morpher.core.compat.swem;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.LivingEntity;

public final class SWEMCompat {

    private SWEMCompat() {
    }

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static String getHorseGaitName(LivingEntity livingEntity) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void registerControllerFunctions(CtrlBinding ctrlBinding) {
        throw new AssertionError();
    }
}
