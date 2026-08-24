package com.micaftic.morpher.network.message;

import com.micaftic.morpher.model.ServerModelManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.micaftic.morpher.core.api.network.PacketContext;

public record C2SModelUploadChunkPacket(long uploadId, int offset, byte[] data, int dataOffset, int dataLength) {

    public C2SModelUploadChunkPacket(long uploadId, int offset, byte[] data) {
        this(uploadId, offset, data, 0, data == null ? 0 : data.length);
    }

    public static void encode(C2SModelUploadChunkPacket message, FriendlyByteBuf buf) {
        buf.writeVarLong(message.uploadId);
        buf.writeVarInt(message.offset);
        buf.writeVarInt(message.dataLength);
        buf.writeBytes(message.data, message.dataOffset, message.dataLength);
    }

    public static C2SModelUploadChunkPacket decode(FriendlyByteBuf buf) {
        return new C2SModelUploadChunkPacket(buf.readVarLong(), buf.readVarInt(), buf.readByteArray());
    }

    public static void handle(C2SModelUploadChunkPacket message, PacketContext ctx) {
        if (ctx.isServerSide() && ctx.getSender() != null) {
            ServerPlayer sender = ctx.getSender();
            ctx.enqueueWork(() -> ServerModelManager.receiveModelUploadChunk(sender, message.uploadId, message.offset, message.data));
        }
    }
}
