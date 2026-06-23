package com.micaftic.morpher.core.compat.create;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import net.minecraft.world.entity.player.Player;

public final class CreateCompat {

    private CreateCompat() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static boolean isPlayerOnCreateContraption(Player player) {
        return false;
    }

    public static void registerCreateFunctions(CtrlBinding binding) {
    }
}
