package com.micaftic.morpher.core.api.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import com.micaftic.morpher.core.api.network.neoforge.YSMChannelImpl;

import java.util.function.BiConsumer;
import java.util.function.Function;

public final class YSMChannel {
    private YSMChannel() {}
    public static void init(ResourceLocation channelId, String version) { YSMChannelImpl.init(channelId, version); }
    public static <T> void register(int discriminator, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder, BiConsumer<T, PacketContext> handler, PacketDirection direction) { YSMChannelImpl.register(discriminator, type, encoder, decoder, handler, direction); }
    public static void sendToServer(Object packet) { YSMChannelImpl.sendToServer(packet); }
    public static void sendToClientPlayer(Object packet, ServerPlayer player) { YSMChannelImpl.sendToClientPlayer(packet, player); }
    public static void sendToAll(Object packet) { YSMChannelImpl.sendToAll(packet); }
    public static void sendToTrackingEntity(Object packet, Entity entity) { YSMChannelImpl.sendToTrackingEntity(packet, entity); }
    public static void sendToTrackingEntityAndSelf(Object packet, Player player) { YSMChannelImpl.sendToTrackingEntityAndSelf(packet, player); }
    public static Packet<?> toClientboundPacket(Object packet) { return YSMChannelImpl.toClientboundPacket(packet); }
    public static Packet<?> toServerboundPacket(Object packet) { return YSMChannelImpl.toServerboundPacket(packet); }
}