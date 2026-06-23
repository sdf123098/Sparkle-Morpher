package com.micaftic.morpher.core.api.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class YSMChannel {
    private static final int MAX_PAYLOAD_SIZE = 64 * 1024 * 1024;
    private static final Map<Integer, Registration<?>> BY_DISCRIMINATOR = new HashMap<>();
    private static final Map<Class<?>, Registration<?>> BY_TYPE = new HashMap<>();

    private static Identifier channelId;
    private static String version;
    private static CustomPacketPayload.Type<YSMPayload> payloadType;

    private YSMChannel() {
    }

    public static void init(Identifier channelId, String version) {
        YSMChannel.channelId = channelId;
        YSMChannel.version = version;
        YSMChannel.payloadType = new CustomPacketPayload.Type<>(channelId);
    }

    public static <T> void register(int discriminator, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder, BiConsumer<T, PacketContext> handler, PacketDirection direction) {
        Registration<T> registration = new Registration<>(discriminator, type, encoder, decoder, handler, direction);
        BY_DISCRIMINATOR.put(discriminator, registration);
        BY_TYPE.put(type, registration);
    }

    public static void sendToServer(Object packet) {
        ClientPacketDistributor.sendToServer(encodePacket(packet));
    }

    public static void sendToClientPlayer(Object packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, encodePacket(packet));
    }

    public static void sendToAll(Object packet) {
        PacketDistributor.sendToAllPlayers(encodePacket(packet));
    }

    public static void sendToTrackingEntity(Object packet, Entity entity) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, encodePacket(packet));
    }

    public static void sendToTrackingEntityAndSelf(Object packet, Player player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, encodePacket(packet));
    }

    public static Packet<?> toClientboundPacket(Object packet) {
        return encodePacket(packet).toVanillaClientbound();
    }

    public static Packet<?> toServerboundPacket(Object packet) {
        return encodePacket(packet).toVanillaServerbound();
    }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        if (payloadType == null || version == null) {
            throw new IllegalStateException("YSM network channel was not initialized before NeoForge payload registration");
        }
        event.registrar(channelId.getNamespace()).versioned(version).optional().playBidirectional(payloadType, YSMPayload.STREAM_CODEC, YSMChannel::handlePayload);
    }

    public static void registerClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        if (payloadType == null) {
            throw new IllegalStateException("YSM network channel was not initialized before NeoForge client payload registration");
        }
        event.register(payloadType, YSMChannel::handlePayload);
    }

    private static YSMPayload encodePacket(Object packet) {
        Registration<?> registration = BY_TYPE.get(packet.getClass());
        if (registration == null) {
            throw new IllegalArgumentException("Unregistered YSM packet type: " + packet.getClass().getName());
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        registration.encode(packet, buffer);
        byte[] data = new byte[buffer.readableBytes()];
        buffer.readBytes(data);
        return new YSMPayload(registration.discriminator(), data);
    }

    private static void handlePayload(YSMPayload payload, IPayloadContext context) {
        Registration<?> registration = BY_DISCRIMINATOR.get(payload.discriminator());
        if (registration == null) {
            throw new IllegalArgumentException("Unknown YSM packet discriminator: " + payload.discriminator());
        }
        PacketDirection actualDirection = context.flow() == PacketFlow.SERVERBOUND ? PacketDirection.PLAY_TO_SERVER : PacketDirection.PLAY_TO_CLIENT;
        if (registration.direction() != actualDirection) {
            throw new IllegalArgumentException("YSM packet discriminator " + payload.discriminator() + " used for " + actualDirection + " but registered for " + registration.direction());
        }
        registration.decodeAndHandle(payload.data(), new NeoForgePacketContext(context));
    }

    private record YSMPayload(int discriminator, byte[] data) implements CustomPacketPayload {
        private static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, YSMPayload> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.ofMember(
                (payload, buffer) -> {
                    buffer.writeVarInt(payload.discriminator);
                    buffer.writeByteArray(payload.data);
                },
                buffer -> new YSMPayload(buffer.readVarInt(), buffer.readByteArray(MAX_PAYLOAD_SIZE))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return payloadType;
        }
    }

    private record Registration<T>(int discriminator, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder, BiConsumer<T, PacketContext> handler, PacketDirection direction) {
        @SuppressWarnings("unchecked")
        private void encode(Object packet, FriendlyByteBuf buffer) {
            encoder.accept((T) packet, buffer);
        }

        private void decodeAndHandle(byte[] data, PacketContext context) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
            T packet = decoder.apply(buffer);
            handler.accept(packet, context);
        }
    }

    private record NeoForgePacketContext(IPayloadContext delegate) implements PacketContext {
        @Override
        public boolean isClientSide() {
            return delegate.flow() == PacketFlow.CLIENTBOUND;
        }

        @Override
        public ServerPlayer getSender() {
            return delegate.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        }

        @Override
        public net.minecraft.network.Connection getConnection() {
            return delegate.connection();
        }

        @Override
        public void enqueueWork(Runnable runnable) {
            delegate.enqueueWork(runnable);
        }
    }
}
