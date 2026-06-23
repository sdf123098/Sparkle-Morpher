package com.micaftic.morpher.network.message;

import com.micaftic.morpher.capability.StarModelsCapability;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.micaftic.morpher.core.api.network.PacketContext;

public class C2SSetStarModelPacket {

    private final String modelId;

    private final boolean isAdd;

    private C2SSetStarModelPacket(String modelId, boolean isAdd) {
        this.modelId = modelId;
        this.isAdd = isAdd;
    }

    public static C2SSetStarModelPacket add(String modelId) {
        return new C2SSetStarModelPacket(modelId, true);
    }

    public static C2SSetStarModelPacket remove(String modelId) {
        return new C2SSetStarModelPacket(modelId, false);
    }

    public static void encode(C2SSetStarModelPacket message, FriendlyByteBuf buf) {
        buf.writeUtf(message.modelId);
        buf.writeBoolean(message.isAdd);
    }

    public static C2SSetStarModelPacket decode(FriendlyByteBuf buf) {
        return new C2SSetStarModelPacket(buf.readUtf(), buf.readBoolean());
    }

    public static void handle(C2SSetStarModelPacket message, PacketContext ctx) {
        if (ctx.isServerSide()) {
            ctx.enqueueWork(() -> {
                ServerPlayer sender = ctx.getSender();
                if (sender == null) {
                    return;
                }
                handleCapability(message, sender);
            });
        }
    }

    private static void handleCapability(C2SSetStarModelPacket message, ServerPlayer sender) {
        StarModelsCapability.get(sender).ifPresent(cap -> {
            if (message.isAdd) {
                cap.addModel(message.modelId);
            } else {
                cap.removeModel(message.modelId);
            }
        });
    }
}