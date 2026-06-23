package com.micaftic.morpher.core.api.network.fabric.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import com.micaftic.morpher.core.api.network.fabric.YSMPayload;

public final class YSMChannelClientImpl {

    private YSMChannelClientImpl() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(YSMPayload.TYPE, (payload, context) ->
                com.micaftic.morpher.core.api.network.fabric.YSMChannelImpl.dispatch(
                        payload.buf(),
                        new ClientPacketContext(context.client(), context.player().connection.getConnection())
                )
        );
    }

    public static void sendToServer(FriendlyByteBuf buf) {
        ClientPlayNetworking.send(new YSMPayload(buf));
    }

    public static Packet<?> toServerboundPacket(FriendlyByteBuf buf) {
        return ClientPlayNetworking.createC2SPacket(new YSMPayload(buf));
    }
}
