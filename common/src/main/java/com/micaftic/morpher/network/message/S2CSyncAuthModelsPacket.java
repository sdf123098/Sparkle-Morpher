package com.micaftic.morpher.network.message;

import com.google.common.collect.Sets;
import com.micaftic.morpher.core.api.network.PacketContext;
import com.micaftic.morpher.network.ClientNetworkBridge;
import net.minecraft.network.FriendlyByteBuf;

import java.util.HashSet;
import java.util.Set;

public class S2CSyncAuthModelsPacket {
    private final Set<String> authModels;

    public S2CSyncAuthModelsPacket(Set<String> authModels) {
        this.authModels = authModels;
    }

    public static void encode(S2CSyncAuthModelsPacket message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.authModels.size());
        for (String modelId : message.authModels) {
            buf.writeUtf(modelId);
        }
    }

    public static S2CSyncAuthModelsPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        HashSet<String> tmp = Sets.newHashSet();
        for (int i = 0; i < size; i++) {
            tmp.add(buf.readUtf());
        }
        return new S2CSyncAuthModelsPacket(tmp);
    }

    public static void handle(S2CSyncAuthModelsPacket message, PacketContext ctx) {
        ClientNetworkBridge.handle(ctx, "handleSyncAuthModels", message);
    }

    public Set<String> getAuthModels() {
        return this.authModels;
    }
}
