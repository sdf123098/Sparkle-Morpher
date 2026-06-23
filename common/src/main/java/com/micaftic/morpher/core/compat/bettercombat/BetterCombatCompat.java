package com.micaftic.morpher.core.compat.bettercombat;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import dev.architectury.injectables.annotations.ExpectPlatform;

public final class BetterCombatCompat {

    private BetterCombatCompat() {
    }

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void registerBindings(CtrlBinding binding) {
        throw new AssertionError();
    }
}
