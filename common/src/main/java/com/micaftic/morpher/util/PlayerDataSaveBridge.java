package com.micaftic.morpher.util;

import com.micaftic.morpher.mixin.PlayerListAccessor;
import com.micaftic.morpher.core.architectury.utils.GameInstance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerDataSaveBridge {

    private PlayerDataSaveBridge() {
    }

    public static void save(ServerPlayer player) {
        MinecraftServer server = GameInstance.getServer();
        if (server != null) {
            ((PlayerListAccessor) server.getPlayerList()).ysm$savePlayer(player);
        }
    }
}
