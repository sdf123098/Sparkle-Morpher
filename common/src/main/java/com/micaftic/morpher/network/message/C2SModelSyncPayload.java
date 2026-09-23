package com.micaftic.morpher.network.message;

import net.minecraft.network.FriendlyByteBuf;
import com.micaftic.morpher.core.api.network.PacketContext;

import java.nio.ByteBuffer;

public class C2SModelSyncPayload {

    private final ByteBuffer data;

    public C2SModelSyncPayload(ByteBuffer data) {
        this.data = data;
    }

    public static void encode(C2SModelSyncPayload message, FriendlyByteBuf buf) {
        buf.writeBytes(message.data);
    }

    public static C2SModelSyncPayload decode(FriendlyByteBuf buf) {
        ByteBuffer data = ByteBuffer.allocate(buf.readableBytes());
        buf.readBytes(data);
        data.flip();
        return new C2SModelSyncPayload(data);
    }

    public static void handle(C2SModelSyncPayload message, PacketContext ctx) {
        // Removed with the server-side SPM model sync protocol. Cloud uploads
        // and downloads are HTTP operations owned by the client Cloud runtime.
    }
}
