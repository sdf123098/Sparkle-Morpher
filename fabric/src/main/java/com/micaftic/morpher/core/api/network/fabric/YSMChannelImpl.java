package com.micaftic.morpher.core.api.network.fabric;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.mixin.ServerCommonPacketListenerImplAccessor;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import com.micaftic.morpher.core.api.network.PacketContext;
import com.micaftic.morpher.core.api.network.PacketDirection;
import com.micaftic.morpher.core.api.network.fabric.client.YSMChannelClientImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class YSMChannelImpl {

    private static final Map<Integer, Codec<?>> CODECS_BY_ID = new HashMap<>();
    private static final Map<Class<?>, Integer> ID_BY_CLASS = new HashMap<>();

    private static Identifier channelId;
    private static volatile MinecraftServer currentServer;

    private YSMChannelImpl() {
    }

    public static void init(Identifier id, String version) {
        channelId = id;

        YSMPayload.initType(channelId);
        PayloadTypeRegistry.serverboundPlay().register(YSMPayload.TYPE, YSMPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(YSMPayload.TYPE, YSMPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(YSMPayload.TYPE, (payload, context) ->
                dispatch(payload.buf(), new ServerPacketContext(
                        context.server(), context.player(),
                        ((ServerCommonPacketListenerImplAccessor) context.player().connection).ysm$getConnection()
                ))
        );

        ServerLifecycleEvents.SERVER_STARTED.register(server -> currentServer = server);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> currentServer = null);

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            YSMChannelClientImpl.init();
        }
    }

    public static <T> void register(int discriminator, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
                                    Function<FriendlyByteBuf, T> decoder, BiConsumer<T, PacketContext> handler,
                                    PacketDirection direction) {
        if ((discriminator & ~0xff) != 0) {
            throw new IllegalArgumentException("Discriminator must fit in an unsigned byte (0-255): " + discriminator);
        }
        Codec<T> codec = new Codec<>(type, encoder, decoder, handler);
        CODECS_BY_ID.put(discriminator & 0xff, codec);
        ID_BY_CLASS.put(type, discriminator & 0xff);
    }

    public static void dispatch(FriendlyByteBuf buf, PacketContext ctx) {
        if (!buf.isReadable()) {
            return;
        }
        int discriminator = -1;
        try {
            discriminator = buf.readUnsignedByte();
            Codec<?> codec = CODECS_BY_ID.get(discriminator);
            if (codec != null) {
                codec.dispatch(buf, ctx);
            }
        } catch (RuntimeException e) {
            YesSteveModel.LOGGER.warn("[SM] Dropped malformed network packet id {}", discriminator, e);
        }
    }

    public static void sendToServer(Object packet) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }
        YSMChannelClientImpl.sendToServer(encode(packet));
    }

    public static void sendToClientPlayer(Object packet, ServerPlayer player) {
        ServerPlayNetworking.send(player, new YSMPayload(encode(packet)));
    }

    public static void sendToAll(Object packet) {
        MinecraftServer server = currentServer;
        if (server == null) {
            return;
        }
        for (ServerPlayer player : PlayerLookup.all(server)) {
            ServerPlayNetworking.send(player, new YSMPayload(encode(packet)));
        }
    }

    public static void sendToTrackingEntity(Object packet, Entity entity) {
        for (ServerPlayer player : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(player, new YSMPayload(encode(packet)));
        }
    }

    public static void sendToTrackingEntityAndSelf(Object packet, Player player) {
        for (ServerPlayer p : PlayerLookup.tracking(player)) {
            ServerPlayNetworking.send(p, new YSMPayload(encode(packet)));
        }
        if (player instanceof ServerPlayer self) {
            ServerPlayNetworking.send(self, new YSMPayload(encode(packet)));
        }
    }

    public static Packet<?> toClientboundPacket(Object packet) {
        return new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(new YSMPayload(encode(packet)));
    }

    public static Packet<?> toServerboundPacket(Object packet) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            throw new IllegalStateException("toServerboundPacket can only be invoked from the client environment");
        }
        return YSMChannelClientImpl.toServerboundPacket(encode(packet));
    }

    static FriendlyByteBuf encode(Object packet) {
        Integer id = ID_BY_CLASS.get(packet.getClass());
        if (id == null) {
            throw new IllegalStateException("Packet type not registered: " + packet.getClass());
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(id & 0xff);
        CODECS_BY_ID.get(id).encode(packet, buf);
        return buf;
    }

    private static void registerPayloadTypes() {
        // MC 26.x Fabric API payload registration - uses direct send approach
        // Payload type registration is handled by registerGlobalReceiver
    }
}
