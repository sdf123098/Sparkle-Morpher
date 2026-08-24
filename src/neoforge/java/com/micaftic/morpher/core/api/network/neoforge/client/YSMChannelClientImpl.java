package com.micaftic.morpher.core.api.network.neoforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.neoforged.neoforge.network.PacketDistributor;
import com.micaftic.morpher.core.api.network.neoforge.YSMPayload;

public final class YSMChannelClientImpl {

    private YSMChannelClientImpl() {
    }

    public static void handleClientPayload(YSMPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        com.micaftic.morpher.core.api.network.neoforge.YSMChannelImpl.dispatch(
                payload.buf(),
                new ClientPacketContext(
                        Minecraft.getInstance(),
                        context.connection()
                )
        );
    }

    public static void sendToServer(FriendlyByteBuf buf) {
        PacketDistributor.sendToServer(new YSMPayload(buf));
    }

    public static Packet<?> toServerboundPacket(FriendlyByteBuf buf) {
        PacketDistributor.sendToServer(new YSMPayload(buf));
        return null;
    }
}
