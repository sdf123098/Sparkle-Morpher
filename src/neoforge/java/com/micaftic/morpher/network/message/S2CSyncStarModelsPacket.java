package com.micaftic.morpher.network.message;

import com.micaftic.morpher.capability.StarModelsCapability;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.util.LocalStarModelsStore;
import com.google.common.collect.Sets;
import com.micaftic.morpher.network.ClientNetworkBridge;
import net.minecraft.network.FriendlyByteBuf;
import com.micaftic.morpher.core.api.network.PacketContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashSet;
import java.util.Set;

public class S2CSyncStarModelsPacket {

    private final Set<String> starModels;

    public S2CSyncStarModelsPacket(Set<String> starModels) {
        this.starModels = starModels;
    }

    public static void encode(S2CSyncStarModelsPacket message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.starModels.size());
        for (String starModel : message.starModels) {
            buf.writeUtf(starModel);
        }
    }

    public static S2CSyncStarModelsPacket decode(FriendlyByteBuf buf) {
        int varInt = buf.readVarInt();
        HashSet<String> tmp = Sets.newHashSet();
        for (int i = 0; i < varInt; i++) {
            tmp.add(buf.readUtf());
        }
        return new S2CSyncStarModelsPacket(tmp);
    }

    public static void handle(S2CSyncStarModelsPacket message, PacketContext ctx) {
        ClientNetworkBridge.handle(ctx, "handleSyncStarModels", message);
    }

    public Set<String> getStarModels() {
        return this.starModels;
    }
}
