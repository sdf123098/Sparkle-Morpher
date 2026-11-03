package com.micaftic.morpher.core.compat.bettercombat;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;

public final class BetterCombatCompat {

    private BetterCombatCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.bettercombat.fabric.BetterCombatCompatImpl.isLoaded();
    }

    public static void registerBindings(CtrlBinding binding) {
        com.micaftic.morpher.core.compat.bettercombat.fabric.BetterCombatCompatImpl.registerBindings(binding);
    }
}
