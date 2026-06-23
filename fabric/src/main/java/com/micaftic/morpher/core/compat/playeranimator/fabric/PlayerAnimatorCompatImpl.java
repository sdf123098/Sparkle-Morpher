package com.micaftic.morpher.core.compat.playeranimator.fabric;

import net.minecraft.client.player.AbstractClientPlayer;

public final class PlayerAnimatorCompatImpl {

    private PlayerAnimatorCompatImpl() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static boolean isPlayerAnimated(AbstractClientPlayer abstractClientPlayer) {
        return false;
    }
}
