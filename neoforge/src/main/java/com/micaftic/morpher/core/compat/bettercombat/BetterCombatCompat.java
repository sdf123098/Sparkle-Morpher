package com.micaftic.morpher.core.compat.bettercombat;

import com.micaftic.morpher.client.core.HostMolangSink;
import dev.spm.core.scene.MolangValue;

public final class BetterCombatCompat {

    private BetterCombatCompat() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static void registerBindings(HostMolangSink binding) {
        binding.var("bcombat_attack_animation", event -> MolangValue.text(""));
    }
}
