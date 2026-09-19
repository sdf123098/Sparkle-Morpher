package com.micaftic.morpher.network.message;

import com.micaftic.morpher.fakeplayer.FakePlayerListService;
import com.micaftic.morpher.core.api.network.PacketContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Requests a server-authoritative snapshot of manageable fake players. */
public final class C2SRequestFakePlayerListPacket {

    public static void encode(C2SRequestFakePlayerListPacket message, FriendlyByteBuf buf) {
    }

    public static C2SRequestFakePlayerListPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestFakePlayerListPacket();
    }

    public static void handle(C2SRequestFakePlayerListPacket message, PacketContext ctx) {
        if (ctx.isServerSide()) {
            ctx.enqueueWork(() -> {
                ServerPlayer sender = ctx.getSender();
                if (sender != null) {
                    FakePlayerListService.sendTo(sender);
                }
            });
        }
    }
}
