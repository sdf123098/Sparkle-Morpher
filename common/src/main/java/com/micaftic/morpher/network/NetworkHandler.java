package com.micaftic.morpher.network;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.mixin.ConnectionAccessor;
import com.micaftic.morpher.mixin.ServerCommonPacketListenerImplAccessor;
import com.micaftic.morpher.network.message.*;
import io.netty.util.AttributeKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import com.micaftic.morpher.core.api.network.PacketDirection;
import com.micaftic.morpher.core.api.network.YSMChannel;

public final class NetworkHandler {

    public static final String VERSION = "2.6.0";

    public static final ResourceLocation CHANNEL_ID = ResourceLocation.fromNamespaceAndPath(YesSteveModel.MOD_ID, VERSION.replace('.', '_'));

    private static final AttributeKey<String> CHANNEL_VERSION_KEY = AttributeKey.valueOf("sparkle_morpher_channel_version");

    private static volatile boolean clientHandshakeComplete = false;

    public static boolean setChannelVersion(Connection connection, String str) {
        return ((ConnectionAccessor) connection).ysm$getChannel().attr(CHANNEL_VERSION_KEY).compareAndSet(null, str);
    }

    public static void markClientHandshakeComplete() {
        clientHandshakeComplete = true;
    }

    public static void resetClientHandshake() {
        clientHandshakeComplete = false;
    }

    public static boolean isPlayerConnected(ServerPlayer serverPlayer) {
        return serverPlayer.connection != null && isConnectionValid(((ServerCommonPacketListenerImplAccessor) serverPlayer.connection).ysm$getConnection());
    }

    public static boolean isClientConnected() {
        if (clientHandshakeComplete) {
            return true;
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return false;
        }
        return isConnectionValid(connection.getConnection());
    }

    public static boolean isConnectionValid(@Nullable Connection connection) {
        return connection != null && ((ConnectionAccessor) connection).ysm$getChannel() != null && VERSION.equals(((ConnectionAccessor) connection).ysm$getChannel().attr(CHANNEL_VERSION_KEY).get());
    }

    public static void init() {
        YSMChannel.init(CHANNEL_ID, VERSION);
        YSMChannel.register(1, S2CModelSyncPayload.class, S2CModelSyncPayload::encode, S2CModelSyncPayload::decode, S2CModelSyncPayload::handle, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(2, C2SModelSyncPayload.class, C2SModelSyncPayload::encode, C2SModelSyncPayload::decode, C2SModelSyncPayload::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(3, S2CExecuteMolangPacket.class, S2CExecuteMolangPacket::encode, S2CExecuteMolangPacket::decode, S2CExecuteMolangPacket::handle, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(4, S2CSetModelAndTexturePacket.class, S2CSetModelAndTexturePacket::encode, S2CSetModelAndTexturePacket::decode, S2CSetModelAndTexturePacket::handle, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(5, C2SRequestSwitchModelPacket.class, C2SRequestSwitchModelPacket::encode, C2SRequestSwitchModelPacket::decode, C2SRequestSwitchModelPacket::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(6, S2CSyncAuthModelsPacket.class, S2CSyncAuthModelsPacket::encode, S2CSyncAuthModelsPacket::decode, S2CSyncAuthModelsPacket::handle, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(7, C2SPlayAnimationPacket.class, C2SPlayAnimationPacket::encode, C2SPlayAnimationPacket::decode, C2SPlayAnimationPacket::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(8, S2CSyncStarModelsPacket.class, S2CSyncStarModelsPacket::encode, S2CSyncStarModelsPacket::decode, S2CSyncStarModelsPacket::handle, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(9, C2SSetStarModelPacket.class, C2SSetStarModelPacket::encode, C2SSetStarModelPacket::decode, C2SSetStarModelPacket::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(15, C2SCompleteFeedbackPacket.class, C2SCompleteFeedbackPacket::encode, C2SCompleteFeedbackPacket::decode, C2SCompleteFeedbackPacket::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(16, S2CSyncProjectileModelPacket.class, S2CSyncProjectileModelPacket::encode, S2CSyncProjectileModelPacket::decode, S2CSyncProjectileModelPacket::handle, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(17, C2SRequestExecuteMolangPacket.class, C2SRequestExecuteMolangPacket::encode, C2SRequestExecuteMolangPacket::decode, C2SRequestExecuteMolangPacket::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(18, C2SSyncAnimationExpressionPacket.class, C2SSyncAnimationExpressionPacket::encode, C2SSyncAnimationExpressionPacket::decode, C2SSyncAnimationExpressionPacket::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(19, S2CSyncAnimationExpressionPacket.class, S2CSyncAnimationExpressionPacket::encode, S2CSyncAnimationExpressionPacket::decode, S2CSyncAnimationExpressionPacket::handleCapability, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(21, S2CSyncPlayerStatePacket.class, S2CSyncPlayerStatePacket::encode, S2CSyncPlayerStatePacket::decode, S2CSyncPlayerStatePacket::handle, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(22, S2CSyncVehicleModelPacket.class, S2CSyncVehicleModelPacket::encode, S2CSyncVehicleModelPacket::decode, S2CSyncVehicleModelPacket::handle, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(23, C2SSwingArmPacket.class, C2SSwingArmPacket::encode, C2SSwingArmPacket::decode, C2SSwingArmPacket::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(51, S2CVersionCheckPacket.class, S2CVersionCheckPacket::encode, S2CVersionCheckPacket::decode, S2CVersionCheckPacket::handle, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(52, C2SVersionCheckPacket.class, C2SVersionCheckPacket::encode, C2SVersionCheckPacket::decode, C2SVersionCheckPacket::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(70, C2SModelUploadStartPacket.class, C2SModelUploadStartPacket::encode, C2SModelUploadStartPacket::decode, C2SModelUploadStartPacket::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(71, S2CModelUploadStartPacket.class, S2CModelUploadStartPacket::encode, S2CModelUploadStartPacket::decode, S2CModelUploadStartPacket::handle, PacketDirection.PLAY_TO_CLIENT);
        YSMChannel.register(72, C2SModelUploadChunkPacket.class, C2SModelUploadChunkPacket::encode, C2SModelUploadChunkPacket::decode, C2SModelUploadChunkPacket::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(73, C2SModelUploadFinishPacket.class, C2SModelUploadFinishPacket::encode, C2SModelUploadFinishPacket::decode, C2SModelUploadFinishPacket::handle, PacketDirection.PLAY_TO_SERVER);
        YSMChannel.register(74, S2CModelUploadResultPacket.class, S2CModelUploadResultPacket::encode, S2CModelUploadResultPacket::decode, S2CModelUploadResultPacket::handle, PacketDirection.PLAY_TO_CLIENT);
    }

    public static void sendToServer(Object obj) {
        if (isClientConnected()) {
            YSMChannel.sendToServer(obj);
        }
    }

    public static void sendToClientPlayer(Object obj, Player player) {
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
