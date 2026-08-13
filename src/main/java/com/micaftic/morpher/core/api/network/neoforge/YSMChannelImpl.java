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
import net.neoforged.neoforge.network.registration.NetworkRegistry;
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

        PayloadRegistrar registrar = event.registrar(channelId.toString()).optional();
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

    /**
     * 客户端连接是否已协商 SPM payload channel。
     *
     * <p>未装 SPM 的客户端（原版/Fabric/低版本）在配置阶段不注册 {@code sparkle_morpher}
     * payload——向其发送会使 {@code NetworkRegistry.checkPacket} 抛
     * {@code UnsupportedOperationException}：NeoForge 端踢玩家，原版/Fabric 客户端
     * 场景异常在服务器 tick 冒泡导致服务器崩溃。发送前必须检测。</p>
     */
    public static boolean canSendToClient(ServerPlayer player) {
        if (player == null || player.connection == null || channelId == null) {
            return false;
        }
        return NetworkRegistry.hasChannel(player.connection, channelId);
    }

    public static void sendToClientPlayer(Object packet, ServerPlayer player) {
        if (!canSendToClient(player)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new YSMPayload(encode(packet)));
    }

    public static void sendToAll(Object packet) {
        MinecraftServer server = currentServer;
        if (server == null) {
            return;
        }
        safeSend(() -> PacketDistributor.sendToAllPlayers(new YSMPayload(encode(packet))));
    }

    public static void sendToTrackingEntity(Object packet, Entity entity) {
        safeSend(() -> PacketDistributor.sendToPlayersTrackingEntity(entity, new YSMPayload(encode(packet))));
    }

    public static void sendToTrackingEntityAndSelf(Object packet, Player player) {
        if (player instanceof ServerPlayer sp && canSendToClient(sp)) {
            PacketDistributor.sendToPlayer(sp, new YSMPayload(encode(packet)));
        }
        safeSend(() -> PacketDistributor.sendToPlayersTrackingEntity(player, new YSMPayload(encode(packet))));
    }

    /** 批量发送无法逐玩家预检：存在未协商 SPM payload 的客户端时整批跳过（防御性兜底）。 */
    private static void safeSend(Runnable send) {
        try {
            send.run();
        } catch (UnsupportedOperationException ignored) {
            // 目标玩家集合含未装 SPM 的客户端——该批数据无法送达，静默跳过
        }
    }

    public static Packet<?> toClientboundPacket(Object packet) {
        // Legacy 握手（LegacyModelSyncProtocol.sendModelData/sendPacket03/05）走 raw
        // connection.send(Packet<?>) 以保留 netty channel 背压控制（sendPacketReliably）。
        // 用 MC 通用自定义 payload 包包装 NeoForge payload：与 sendToClientPlayer 的
        // PacketDistributor 路径等价，客户端经注册的 YSMPayload.TYPE 分发到 handleClientPayload。
        // 修复前此处返回 null → connection.send(null) → Netty NPE → packet01 永不发 → 客户端卡"接收模型数据"。
        return new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(new YSMPayload(encode(packet)));
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
