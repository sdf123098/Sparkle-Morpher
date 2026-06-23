package com.micaftic.morpher.network.message;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import com.micaftic.morpher.core.api.network.PacketContext;

public class S2CVersionCheckPacket {

    private final String version;
    private final boolean oysmServer;
    private final boolean allowUpload;

    public S2CVersionCheckPacket() {
        this(NetworkHandler.VERSION, true, ServerModelManager.isModelUploadAllowed());
    }

    private S2CVersionCheckPacket(String version, boolean oysmServer, boolean allowUpload) {
        this.version = version;
        this.oysmServer = oysmServer;
        this.allowUpload = allowUpload;
    }

    public static S2CVersionCheckPacket decode(FriendlyByteBuf buf) {
        String version = buf.readUtf();
        boolean oysmServer = NetworkHandler.VERSION.equals(version);
        boolean allowUpload = oysmServer;
        if (buf.readableBytes() == 1) {
            oysmServer = true;
            allowUpload = buf.readBoolean();
        } else if (buf.readableBytes() > 1) {
            int readerIndex = buf.readerIndex();
            try {
                String brand = buf.readUtf();
                if ("open_ysm:v1".equals(brand)) {
                    oysmServer = true;
                    if (buf.readableBytes() > 0) {
                        allowUpload = buf.readBoolean();
                    }
                }
            } catch (RuntimeException ignored) {
                buf.readerIndex(readerIndex);
                if (buf.readableBytes() > 0) {
                    oysmServer = true;
                    allowUpload = buf.readBoolean();
                }
            }
        }
        return new S2CVersionCheckPacket(version, oysmServer, allowUpload);
    }

    public static void encode(S2CVersionCheckPacket message, FriendlyByteBuf buf) {
        buf.writeUtf(message.version);
        buf.writeUtf("open_ysm:v1");
        buf.writeBoolean(message.allowUpload);
    }

    public static void handle(S2CVersionCheckPacket message, PacketContext ctx) {
        ctx.enqueueWork(() -> {
            ClientModelManager.setOysmServer(message.oysmServer);
            ClientModelManager.setAllowUpload(message.allowUpload);
        });
        if (NetworkHandler.setChannelVersion(ctx.getConnection(), message.version)) {
            ctx.enqueueWork(ClientModelManager::onSyncConnected);
        }
        if (NetworkHandler.VERSION.equals(message.version)) {
            NetworkHandler.markClientHandshakeComplete();
        }
        ctx.enqueueWork(() -> NetworkHandler.sendToServer(new C2SVersionCheckPacket()));
    }
}
