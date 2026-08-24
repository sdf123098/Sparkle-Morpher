package com.micaftic.morpher.core.api.network;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public interface PacketContext {

    boolean isClientSide();

    default boolean isServerSide() {
        return !isClientSide();
    }

    @Nullable
    ServerPlayer getSender();

    Connection getConnection();

    void enqueueWork(Runnable runnable);
}
