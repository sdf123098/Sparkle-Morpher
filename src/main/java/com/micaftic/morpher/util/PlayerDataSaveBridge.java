package com.micaftic.morpher.util;

import net.minecraft.server.level.ServerPlayer;

public final class PlayerDataSaveBridge {
    private PlayerDataSaveBridge() {}
    public static void save(ServerPlayer player) { player.save(null); }
}