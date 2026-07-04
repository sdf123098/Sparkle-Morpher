package com.micaftic.morpher.network.message;

import com.micaftic.morpher.core.api.network.PacketContext;
import com.micaftic.morpher.network.ClientNetworkBridge;
import net.minecraft.network.FriendlyByteBuf;

public record S2CModelUploadResultPacket(long uploadId, byte status, String modelId, long h1, long h2, String message) {
    public static void encode(S2CModelUploadResultPacket packet, FriendlyByteBuf buf) {
        buf.writeVarLong(packet.uploadId);
        buf.writeByte(packet.status);
        buf.writeUtf(packet.modelId);
        buf.writeVarLong(packet.h1);
        buf.writeVarLong(packet.h2);
        buf.writeUtf(packet.message);
    }

    public static S2CModelUploadResultPacket decode(FriendlyByteBuf buf) {
        return new S2CModelUploadResultPacket(buf.readVarLong(), buf.readByte(), buf.readUtf(), buf.readVarLong(), buf.readVarLong(), buf.readUtf());
    }

    public static void handle(S2CModelUploadResultPacket packet, PacketContext ctx) {
        ClientNetworkBridge.handle(ctx, "handleModelUploadResult", packet);
    }
}
