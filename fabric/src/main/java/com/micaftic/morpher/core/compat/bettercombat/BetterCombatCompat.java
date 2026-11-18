package com.micaftic.morpher.core.compat.bettercombat;

import com.micaftic.morpher.client.core.HostMolangSink;

public final class BetterCombatCompat {

    private BetterCombatCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.bettercombat.fabric.BetterCombatCompatImpl.isLoaded();
    }

    public static void registerBindings(HostMolangSink binding) {
        com.micaftic.morpher.core.compat.bettercombat.fabric.BetterCombatCompatImpl.registerBindings(binding);
    }
}
