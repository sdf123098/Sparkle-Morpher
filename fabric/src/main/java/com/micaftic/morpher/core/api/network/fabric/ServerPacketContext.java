package com.micaftic.morpher.core.api.network.fabric;

import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import com.micaftic.morpher.core.api.network.PacketContext;

final class ServerPacketContext implements PacketContext {

    private final MinecraftServer server;
    private final ServerPlayer player;
    private final Connection connection;

    ServerPacketContext(MinecraftServer server, ServerPlayer player, Connection connection) {
        this.server = server;
        this.player = player;
        this.connection = connection;
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Override
    public @Nullable ServerPlayer getSender() {
        return player;
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public void enqueueWork(Runnable runnable) {
        server.execute(runnable);
    }
}
