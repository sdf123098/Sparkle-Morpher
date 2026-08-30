package com.micaftic.morpher.network;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.mixin.ConnectionAccessor;
import com.micaftic.morpher.mixin.ServerCommonPacketListenerImplAccessor;
import com.micaftic.morpher.network.message.*;
import io.netty.util.AttributeKey;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import com.micaftic.morpher.core.api.network.PacketDirection;
import com.micaftic.morpher.core.api.network.YSMChannel;
import com.micaftic.morpher.legacy.compat.LegacyCompatNetwork;
import com.micaftic.morpher.legacy.compat.LegacyCompatState;
import com.micaftic.morpher.core.api.network.state.PrivacyState;
import com.micaftic.morpher.network.protocol.*;
import com.micaftic.morpher.network.state.MinecraftConnectionState;

public final class NetworkHandler {

    public static final String VERSION = "2.6.0";

    public static final ResourceLocation CHANNEL_ID = com.micaftic.morpher.core.api.resource.ResourceApi.nativeId(YesSteveModel.MOD_ID, VERSION.replace('.', '_'));

    /** 服务端按连接记录的 SPM 协商版本（平台存储：netty Channel attribute，R9.2 保留在本层）。 */
    private static final AttributeKey<String> CHANNEL_VERSION_KEY = AttributeKey.valueOf("sparkle_morpher_channel_version");

    public static boolean setChannelVersion(Connection connection, String str) {
        return ((ConnectionAccessor) connection).ysm$getChannel().attr(CHANNEL_VERSION_KEY).compareAndSet(null, str);
    }

    public static void markClientHandshakeComplete() {
        LegacyCompatState.markClientComplete();
    }

    /** 复位客户端 legacy 会话（握手完成 + oySm/allowUpload 能力），用于退出服务器 / 进入隐私模式。 */
    public static void resetClientHandshake() {
        LegacyCompatState.resetClientSession();
    }

    public static boolean isPlayerConnected(ServerPlayer serverPlayer) {
        return MinecraftConnectionState.isPlayerConnected(serverPlayer)
                && isConnectionValid(((ServerCommonPacketListenerImplAccessor) serverPlayer.connection).ysm$getConnection());
    }

    public static boolean isClientConnected() {
        // R9.2：隐私未激活 + legacy 会话活跃（握手完成，或当前 MC 连接已协商 SPM channel）
        return PrivacyState.isInactive()
                && LegacyCompatState.isClientSessionActive(clientChannelNegotiated());
    }

    private static boolean clientChannelNegotiated() {
        if (!MinecraftConnectionState.isClientConnected()) {
            return false;
        }
        return isConnectionValid(MinecraftConnectionState.clientConnection().getConnection());
    }

    public static boolean isConnectionValid(@Nullable Connection connection) {
        return connection != null && ((ConnectionAccessor) connection).ysm$getChannel() != null && VERSION.equals(((ConnectionAccessor) connection).ysm$getChannel().attr(CHANNEL_VERSION_KEY).get());
    }

    public static void init() {
        YSMChannel.init(CHANNEL_ID, VERSION);
        LegacyCompatNetwork.register();
        AnimationProtocol.register();
        EntityModelProtocol.register();
        ServerPolicyProtocol.register();
        UploadProtocol.register();
    }

    public static void sendToServer(Object obj) {
        // R9.2：隐私检查已内建于 isClientConnected（PrivacyState），不再重复判断
        if (isClientConnected()) {
            YSMChannel.sendToServer(obj);
        }
    }

    public static void sendToClientPlayer(Object obj, Player player) {
        // R9.1：统一发送入口预检——客户端未协商 SPM channel（未装 SPM）时跳过，
        // 避免 NetworkRegistry.checkPacket / fabric ServerPlayNetworking 抛异常崩溃。
        if (player instanceof ServerPlayer serverPlayer && !YSMChannel.canSendToClient(serverPlayer)) {
            return;
        }
        YSMChannel.sendToClientPlayer(obj, (ServerPlayer) player);
    }

    public static void sendToAll(Object obj) {
        YSMChannel.sendToAll(obj);
    }

    public static void sendToTrackingEntity(Object obj, Entity entity) {
        YSMChannel.sendToTrackingEntity(obj, entity);
    }

    public static void sendToTrackingEntityAndSelf(Object obj, Player player) {
        YSMChannel.sendToTrackingEntityAndSelf(obj, player);
    }

    public static Packet<?> toClientboundPacket(Object obj) {
        return YSMChannel.toClientboundPacket(obj);
    }

    public static Packet<?> toServerboundPacket(Object obj) {
        return YSMChannel.toServerboundPacket(obj);
    }
}
