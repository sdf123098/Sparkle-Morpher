package com.micaftic.morpher.network.message;

import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.micaftic.morpher.core.api.network.PacketContext;

public record C2SModelUploadStartPacket(String modelId, String fileName, int totalBytes, String sha256) {

    public static void encode(C2SModelUploadStartPacket message, FriendlyByteBuf buf) {
        buf.writeUtf(message.modelId);
        buf.writeUtf(message.fileName == null ? "" : message.fileName);
        buf.writeVarInt(message.totalBytes);
        buf.writeUtf(message.sha256);
    }

    public static C2SModelUploadStartPacket decode(FriendlyByteBuf buf) {
        return new C2SModelUploadStartPacket(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readUtf());
    }

    public static void handle(C2SModelUploadStartPacket message, PacketContext ctx) {
        if (ctx.isServerSide() && ctx.getSender() != null) {
            ServerPlayer sender = ctx.getSender();
            ctx.enqueueWork(() -> {
                ServerModelManager.UploadStartResult result = ServerModelManager.beginModelUpload(sender, message.modelId, message.fileName, message.totalBytes, message.sha256);
                NetworkHandler.sendToClientPlayer(new S2CModelUploadStartPacket(result.uploadId(), result.status(), result.chunkSize(), result.maxTotalBytes(), result.chunksPerTick(), result.message()), sender);
            });
        }
    }
}
