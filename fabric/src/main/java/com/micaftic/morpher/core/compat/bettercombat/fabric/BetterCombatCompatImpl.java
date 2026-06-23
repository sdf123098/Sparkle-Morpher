package com.micaftic.morpher.core.compat.bettercombat.fabric;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;

public final class BetterCombatCompatImpl {

    private BetterCombatCompatImpl() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static void registerBindings(CtrlBinding binding) {
        binding.clientPlayerEntityVar("bcombat_attack_animation", ctx -> StringPool.EMPTY);
    }
}
