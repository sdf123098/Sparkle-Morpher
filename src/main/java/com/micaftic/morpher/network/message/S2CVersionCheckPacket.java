package com.micaftic.morpher.network.message;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import com.micaftic.morpher.core.api.network.PacketContext;

public class S2CVersionCheckPacket {

    private final String version;

    public S2CVersionCheckPacket() {
        this(NetworkHandler.VERSION);
    }

    private S2CVersionCheckPacket(String version) {
        this.version = version;
    }

    public static S2CVersionCheckPacket decode(FriendlyByteBuf buf) {
        String version = buf.readUtf();
        if(buf.readableBytes() > 0){
            String brand = buf.readUtf();
            if(brand.equals("open_ysm:v1")){
                ClientModelManager.setOysmServer(true);
                ClientModelManager.setAllowUpload(buf.readBoolean());
            }
        }
        return new S2CVersionCheckPacket(version);
    }

    public static void encode(S2CVersionCheckPacket message, FriendlyByteBuf buf) {
        buf.writeUtf(message.version);
        buf.writeUtf("open_ysm:v1");
        buf.writeBoolean(ServerModelManager.isModelUploadAllowed());
    }

    public static void handle(S2CVersionCheckPacket message, PacketContext ctx) {
        if (NetworkHandler.setChannelVersion(ctx.getConnection(), message.version)) {
            ctx.enqueueWork(ClientModelManager::onSyncConnected);
        }
        if (NetworkHandler.VERSION.equals(message.version)) {
            NetworkHandler.markClientHandshakeComplete();
        }
        ctx.enqueueWork(() -> NetworkHandler.sendToServer(new C2SVersionCheckPacket()));
    }
}
