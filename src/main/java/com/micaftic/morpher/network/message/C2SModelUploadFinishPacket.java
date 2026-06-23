package com.micaftic.morpher.network.message;

import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.util.YSMThreadPool;
import com.micaftic.morpher.core.architectury.utils.GameInstance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.micaftic.morpher.core.api.network.PacketContext;

public record C2SModelUploadFinishPacket(long uploadId) {

    public static void encode(C2SModelUploadFinishPacket message, FriendlyByteBuf buf) {
        buf.writeVarLong(message.uploadId);
    }

    public static C2SModelUploadFinishPacket decode(FriendlyByteBuf buf) {
        return new C2SModelUploadFinishPacket(buf.readVarLong());
    }

    public static void handle(C2SModelUploadFinishPacket message, PacketContext ctx) {
        if (ctx.isServerSide() && ctx.getSender() != null) {
            ServerPlayer sender = ctx.getSender();
            ctx.enqueueWork(() -> YSMThreadPool.submit(() -> {
                ServerModelManager.UploadFinishResult result = ServerModelManager.finishModelUpload(sender, message.uploadId);
                MinecraftServer server = GameInstance.getServer();
                Runnable sendResult = () -> NetworkHandler.sendToClientPlayer(new S2CModelUploadResultPacket(result.uploadId(), result.status(), result.modelId(), result.hash1(), result.hash2(), result.message()), sender);
                if (server != null) {
                    server.execute(sendResult);
                } else {
                    sendResult.run();
                }
            }));
        }
    }
}
