package com.micaftic.morpher.core.api.network.fabric.client;

import com.micaftic.morpher.mixin.client.MinecraftAccessor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import com.micaftic.morpher.core.api.network.fabric.YSMPayload;

public final class YSMChannelClientImpl {

    private YSMChannelClientImpl() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(YSMPayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            ClientPacketListener listener = ((MinecraftAccessor) client).ysm$getConnection();
            Connection connection = listener != null ? listener.getConnection() : null;
            if (connection == null && client.player != null) {
                connection = client.player.connection.getConnection();
            }
            if (connection == null) {
                return;
            }
            com.micaftic.morpher.core.api.network.fabric.YSMChannelImpl.dispatch(
                    payload.buf(),
                    new ClientPacketContext(client, connection)
            );
        });
    }

    public static void sendToServer(FriendlyByteBuf buf) {
        ClientPlayNetworking.send(new YSMPayload(buf));
    }

    public static Packet<?> toServerboundPacket(FriendlyByteBuf buf) {
        return new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(new YSMPayload(buf));
    }
}
