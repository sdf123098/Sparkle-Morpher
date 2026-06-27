package com.micaftic.morpher.network.message;

import com.micaftic.morpher.client.ClientModelManager;
import net.minecraft.network.FriendlyByteBuf;
import com.micaftic.morpher.core.api.network.PacketContext;

import java.nio.ByteBuffer;

public class S2CModelSyncPayload {

    private final ByteBuffer data;

    public S2CModelSyncPayload(ByteBuffer data) {
        this.data = data;
    }

    public static void encode(S2CModelSyncPayload message, FriendlyByteBuf buf) {
        buf.writeBytes(message.data);
    }

    public static S2CModelSyncPayload decode(FriendlyByteBuf buf) {
        ByteBuffer data = ByteBuffer.allocate(buf.readableBytes());
        buf.readBytes(data);
        data.flip();
        return new S2CModelSyncPayload(data);
    }

    public static void handle(S2CModelSyncPayload message, PacketContext ctx) {
        if (ctx.isClientSide()) {
            ClientModelManager.startSync(ctx.getConnection(), message.data);
        }
    }
}
