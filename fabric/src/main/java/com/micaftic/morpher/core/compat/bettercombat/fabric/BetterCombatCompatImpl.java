package com.micaftic.morpher.core.compat.bettercombat.fabric;

import com.micaftic.morpher.client.core.HostMolangSink;
import dev.spm.core.scene.MolangValue;

public final class BetterCombatCompatImpl {

    private BetterCombatCompatImpl() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static void registerBindings(HostMolangSink binding) {
        binding.var("bcombat_attack_animation", event -> MolangValue.text(""));
    }
}
