package com.micaftic.morpher.core.api.network.neoforge;

import com.micaftic.morpher.mixin.ServerCommonPacketListenerImplAccessor;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import com.micaftic.morpher.core.api.network.PacketContext;
import com.micaftic.morpher.core.api.network.PacketDirection;
import com.micaftic.morpher.core.api.network.neoforge.client.YSMChannelClientImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class YSMChannelImpl {

    private static final Map<Integer, Codec<?>> CODECS_BY_ID = new HashMap<>();
    private static final Map<Class<?>, Integer> ID_BY_CLASS = new HashMap<>();

    private static ResourceLocation channelId;
    private static volatile MinecraftServer currentServer;

    private YSMChannelImpl() {
    }

    public static void init(ResourceLocation id, String version) {
        channelId = id;
        // Register server lifecycle listeners once
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStartedEvent e) -> currentServer = e.getServer()
        );
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppingEvent e) -> currentServer = null
        );
    }

    private static boolean registered = false;

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        if (registered) return;
        registered = true;

        PayloadRegistrar registrar = event.registrar(channelId.toString());
        YSMPayload.initType(channelId);

        registrar.playBidirectional(YSMPayload.TYPE, YSMPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer sp) {
                dispatch(payload.buf(), new ServerPacketContext(
                        sp.serverLevel().getServer(),
                        sp,
                        ((ServerCommonPacketListenerImplAccessor) sp.connection).ysm$getConnection()
                ));
            } else if (FMLEnvironment.dist == Dist.CLIENT) {
                YSMChannelClientImpl.handleClientPayload(payload, context);
            }
        });
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
        int discriminator = buf.readUnsignedByte();
        Codec<?> codec = CODECS_BY_ID.get(discriminator);
        if (codec != null) {
            codec.dispatch(buf, ctx);
        }
    }

    public static void sendToServer(Object packet) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        YSMChannelClientImpl.sendToServer(encode(packet));
    }

    public static void sendToClientPlayer(Object packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new YSMPayload(encode(packet)));
    }

    public static void sendToAll(Object packet) {
        MinecraftServer server = currentServer;
        if (server == null) {
            return;
        }
        PacketDistributor.sendToAllPlayers(new YSMPayload(encode(packet)));
    }

    public static void sendToTrackingEntity(Object packet, Entity entity) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, new YSMPayload(encode(packet)));
    }

    public static void sendToTrackingEntityAndSelf(Object packet, Player player) {
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new YSMPayload(encode(packet)));
        }
        PacketDistributor.sendToPlayersTrackingEntity(player, new YSMPayload(encode(packet)));
    }

    public static Packet<?> toClientboundPacket(Object packet) {
        return null; // NeoForge: toClientboundPacket not directly supported
    }

    public static Packet<?> toServerboundPacket(Object packet) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            throw new IllegalStateException("toServerboundPacket can only be invoked from the client environment");
        }
        return YSMChannelClientImpl.toServerboundPacket(encode(packet));
    }

    public static FriendlyByteBuf encode(Object packet) {
        Integer id = ID_BY_CLASS.get(packet.getClass());
        if (id == null) {
            throw new IllegalStateException("Packet type not registered: " + packet.getClass());
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(id & 0xff);
        CODECS_BY_ID.get(id).encode(packet, buf);
        return buf;
    }
}
