package com.micaftic.morpher.network.message;

import com.micaftic.morpher.client.upload.ModelUploadSession;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.FriendlyByteBuf;
import com.micaftic.morpher.core.api.network.PacketContext;

public record S2CModelUploadStartPacket(long uploadId, byte status, int chunkSize, int maxTotalBytes, int chunksPerTick, String message) {
    public static void encode(S2CModelUploadStartPacket packet, FriendlyByteBuf buf) {
        buf.writeVarLong(packet.uploadId);
        buf.writeByte(packet.status);
        buf.writeVarInt(packet.chunkSize);
        buf.writeVarInt(packet.maxTotalBytes);
        buf.writeVarInt(packet.chunksPerTick);
        buf.writeUtf(packet.message);
    }

    public static S2CModelUploadStartPacket decode(FriendlyByteBuf buf) {
        long uploadId = buf.readVarLong();
        byte status = buf.readByte();
        int chunkSize = buf.readVarInt();
        int maxTotalBytes = buf.readVarInt();
        int chunksPerTick = buf.readVarInt();
        String message = buf.readUtf();
        return new S2CModelUploadStartPacket(uploadId, status, chunkSize, maxTotalBytes, chunksPerTick, message);
    }

    public static void handle(S2CModelUploadStartPacket packet, PacketContext ctx) {
        if (ctx.isClientSide()) {
            ctx.enqueueWork(() -> handleOnClient(packet));
        }
    }

    @Environment(EnvType.CLIENT)
    private static void handleOnClient(S2CModelUploadStartPacket packet) {
        ModelUploadSession.onStartAck(packet.uploadId, packet.status, packet.chunkSize, packet.maxTotalBytes, packet.chunksPerTick, packet.message);
    }
}
