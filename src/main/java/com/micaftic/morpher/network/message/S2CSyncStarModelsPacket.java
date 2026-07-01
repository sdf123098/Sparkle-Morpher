package com.micaftic.morpher.network.message;

import com.micaftic.morpher.capability.StarModelsCapability;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.util.LocalStarModelsStore;
import com.google.common.collect.Sets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import com.micaftic.morpher.core.api.network.PacketContext;

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
        if (ctx.isClientSide()) {
            ctx.enqueueWork(() -> handleCapability(message));
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleCapability(S2CSyncStarModelsPacket message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            Set<String> merged = Sets.newHashSet(message.starModels);
            Set<String> local = LocalStarModelsStore.load();
            merged.addAll(local);
            LocalStarModelsStore.save(merged);
            StarModelsCapability.get(minecraft.player).ifPresent(cap -> cap.setStarModels(merged));
            for (String modelId : local) {
                if (!message.starModels.contains(modelId) && NetworkHandler.isClientConnected()) {
                    NetworkHandler.sendToServer(C2SSetStarModelPacket.add(modelId));
                }
            }
        }
    }
}
