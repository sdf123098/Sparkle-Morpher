package com.micaftic.morpher.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class ClientNetworkBridge {
    private ClientNetworkBridge() {}

    public static boolean isPhysicalClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    public static boolean isLocalPlayer(Object player) {
        if (!isPhysicalClient() || player == null) return false;
        try {
            Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
            Object instance = minecraft.getMethod("getInstance").invoke(null);
            return minecraft.getField("player").get(instance) == player;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
