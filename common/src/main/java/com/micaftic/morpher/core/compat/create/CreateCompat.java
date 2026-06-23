package com.micaftic.morpher.core.compat.create;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import net.minecraft.world.entity.player.Player;

public final class CreateCompat {

    private CreateCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.create.fabric.CreateCompatImpl.isLoaded();
    }

    public static boolean isPlayerOnCreateContraption(Player player) {
        return com.micaftic.morpher.core.compat.create.fabric.CreateCompatImpl.isPlayerOnCreateContraption(player);
    }

    public static void registerCreateFunctions(CtrlBinding binding) {
        com.micaftic.morpher.core.compat.create.fabric.CreateCompatImpl.registerCreateFunctions(binding);
    }
}
