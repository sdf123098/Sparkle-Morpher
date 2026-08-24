package com.micaftic.morpher.network.message;

import com.micaftic.morpher.core.api.network.PacketContext;
import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidModelSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class C2SSetMaidModelPacket {
    private final int maidId;
    private final String modelId;
    private final String textureId;

    public C2SSetMaidModelPacket(int maidId, String modelId, String textureId) {
        this.maidId = maidId;
        this.modelId = modelId;
        this.textureId = textureId == null ? "" : textureId;
    }

    public static void encode(C2SSetMaidModelPacket message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.maidId);
        buf.writeUtf(message.modelId);
        buf.writeUtf(message.textureId);
    }

    public static C2SSetMaidModelPacket decode(FriendlyByteBuf buf) {
        return new C2SSetMaidModelPacket(buf.readVarInt(), buf.readUtf(), buf.readUtf());
    }

    public static void handle(C2SSetMaidModelPacket message, PacketContext ctx) {
        if (!ctx.isServerSide()) {
            return;
        }
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) {
                return;
            }
            Entity maid = sender.level().getEntity(message.maidId);
            MaidModelSync.applySelectedModel(maid, sender, message.modelId, message.textureId);
        });
    }
}
