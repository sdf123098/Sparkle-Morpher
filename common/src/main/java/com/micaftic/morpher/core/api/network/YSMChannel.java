package com.micaftic.morpher.core.api.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.function.BiConsumer;
import java.util.function.Function;

public final class YSMChannel {

    private YSMChannel() {
    }

    public static void init(Identifier channelId, String version) {
        com.micaftic.morpher.core.api.network.fabric.YSMChannelImpl.init(channelId, version);
    }

    public static <T> void register(int discriminator, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder, BiConsumer<T, PacketContext> handler, PacketDirection direction) {
        com.micaftic.morpher.core.api.network.fabric.YSMChannelImpl.register(discriminator, type, encoder, decoder, handler, direction);
    }

    public static void sendToServer(Object packet) {
        com.micaftic.morpher.core.api.network.fabric.YSMChannelImpl.sendToServer(packet);
    }

    public static void sendToClientPlayer(Object packet, ServerPlayer player) {
        com.micaftic.morpher.core.api.network.fabric.YSMChannelImpl.sendToClientPlayer(packet, player);
    }

    public static void sendToAll(Object packet) {
        com.micaftic.morpher.core.api.network.fabric.YSMChannelImpl.sendToAll(packet);
    }

    public static void sendToTrackingEntity(Object packet, Entity entity) {
        com.micaftic.morpher.core.api.network.fabric.YSMChannelImpl.sendToTrackingEntity(packet, entity);
    }

    public static void sendToTrackingEntityAndSelf(Object packet, Player player) {
        com.micaftic.morpher.core.api.network.fabric.YSMChannelImpl.sendToTrackingEntityAndSelf(packet, player);
    }

    public static Packet<?> toClientboundPacket(Object packet) {
        return com.micaftic.morpher.core.api.network.fabric.YSMChannelImpl.toClientboundPacket(packet);
    }

    public static Packet<?> toServerboundPacket(Object packet) {
        return com.micaftic.morpher.core.api.network.fabric.YSMChannelImpl.toServerboundPacket(packet);
    }
}
