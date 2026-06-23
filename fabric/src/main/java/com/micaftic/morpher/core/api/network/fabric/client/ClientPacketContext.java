package com.micaftic.morpher.core.api.network.fabric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import com.micaftic.morpher.core.api.network.PacketContext;

final class ClientPacketContext implements PacketContext {

    private final Minecraft client;
    private final Connection connection;

    ClientPacketContext(Minecraft client, Connection connection) {
        this.client = client;
        this.connection = connection;
    }

    @Override
    public boolean isClientSide() {
        return true;
    }

    @Override
    public @Nullable ServerPlayer getSender() {
        return null;
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public void enqueueWork(Runnable runnable) {
        client.execute(runnable);
    }
}
