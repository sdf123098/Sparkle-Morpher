package com.micaftic.morpher.core.compat.playeranimator;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.player.AbstractClientPlayer;

public final class PlayerAnimatorCompat {

    private PlayerAnimatorCompat() {
    }

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isPlayerAnimated(AbstractClientPlayer abstractClientPlayer) {
        throw new AssertionError();
    }
}
