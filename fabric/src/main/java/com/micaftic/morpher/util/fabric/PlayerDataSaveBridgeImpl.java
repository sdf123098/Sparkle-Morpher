package com.micaftic.morpher.util.fabric;

import com.micaftic.morpher.core.architectury.utils.GameInstance;
import com.micaftic.morpher.fabric.mixin.PlayerListAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerDataSaveBridgeImpl {

    private PlayerDataSaveBridgeImpl() {
    }

    public static void save(ServerPlayer player) {
        MinecraftServer server = GameInstance.getServer();
        if (server != null) {
            ((PlayerListAccessor) server.getPlayerList()).ysm$savePlayer(player);
        }
    }
}
