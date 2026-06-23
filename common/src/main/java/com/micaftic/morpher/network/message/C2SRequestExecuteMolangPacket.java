package com.micaftic.morpher.network.message;

import com.micaftic.morpher.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import com.micaftic.morpher.core.api.network.PacketContext;

public class C2SRequestExecuteMolangPacket {

    private final String animationName;

    private final int entityId;

    public C2SRequestExecuteMolangPacket(String str, int i) {
        this.animationName = str;
        this.entityId = i;
    }

    public static void encode(C2SRequestExecuteMolangPacket message, FriendlyByteBuf buf) {
        buf.writeUtf(message.animationName);
        buf.writeVarInt(message.entityId);
    }

    public static C2SRequestExecuteMolangPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestExecuteMolangPacket(buf.readUtf(), buf.readVarInt());
    }

    public static void handle(C2SRequestExecuteMolangPacket message, PacketContext ctx) {
        if (ctx.isServerSide()) {
            ctx.enqueueWork(() -> handleOnServer(message, ctx.getSender()));
        }
    }

    public static void handleOnServer(C2SRequestExecuteMolangPacket message, ServerPlayer sender) {
        Entity entity;
        if (sender == null || !sender.isAlive() || (entity = sender.level().getEntity(message.entityId)) == null) {
            return;
        }
        NetworkHandler.sendToTrackingEntity(new S2CExecuteMolangPacket(message.entityId, message.animationName), entity);
    }
}