package com.micaftic.morpher.core.compat.playeranimator;

import net.minecraft.client.player.AbstractClientPlayer;

public final class PlayerAnimatorCompat {

    private PlayerAnimatorCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.playeranimator.fabric.PlayerAnimatorCompatImpl.isLoaded();
    }

    public static boolean isPlayerAnimated(AbstractClientPlayer abstractClientPlayer) {
        return com.micaftic.morpher.core.compat.playeranimator.fabric.PlayerAnimatorCompatImpl.isPlayerAnimated(abstractClientPlayer);
    }
}
