package com.micaftic.morpher.network;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
/** Retired compatibility boundary. Minecraft-server model networking has been removed. */
@Deprecated
public final class NetworkHandler {
    public static final String VERSION = "2.6.0";
    private NetworkHandler() {}
    public static void init() {}
    public static boolean setChannelVersion(Connection connection, String version) { return false; }
    public static void markClientHandshakeComplete() {}
    public static void resetClientHandshake() {}
    public static boolean isPlayerConnected(ServerPlayer player) { return false; }
    public static boolean isClientConnected() { return false; }
    public static boolean isConnectionValid(Connection connection) { return false; }
    public static void sendToServer(Object value) {}
    public static void sendToClientPlayer(Object value, Player player) {}
    public static void sendToAll(Object value) {}
    public static void sendToTrackingEntity(Object value, Entity entity) {}
    public static void sendToTrackingEntityAndSelf(Object value, Player player) {}
    public static Packet<?> toClientboundPacket(Object value) { return null; }
    public static Packet<?> toServerboundPacket(Object value) { return null; }
}
