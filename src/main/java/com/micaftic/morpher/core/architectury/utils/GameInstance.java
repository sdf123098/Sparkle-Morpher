package com.micaftic.morpher.core.architectury.utils;

import net.minecraft.server.MinecraftServer;

/**
 * Tracks the active server for common code that previously used Architectury.
 */
public class GameInstance {
    private static volatile MinecraftServer server;

    public static MinecraftServer getServer() {
        return server;
    }

    public static void setServer(MinecraftServer server) {
        GameInstance.server = server;
    }
}
