package com.micaftic.morpher.util;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerDataSaveBridge {

    private PlayerDataSaveBridge() {
    }

    @ExpectPlatform
    public static void save(ServerPlayer player) {
        throw new AssertionError();
    }
}
