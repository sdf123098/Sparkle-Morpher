package com.micaftic.morpher.fabric;

import com.micaftic.morpher.peer.PeerLanBootstrap;
import com.micaftic.morpher.network.message.PeerLanPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class PeerLanNetworking {
    private PeerLanNetworking() {}
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(PeerLanPayload.TYPE, PeerLanPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PeerLanPayload.TYPE, PeerLanPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PeerLanPayload.TYPE, (payload, context) -> {
            if(!payload.request() || !ServerPlayNetworking.canSend(context.player(), PeerLanPayload.TYPE)) return;
            String invite = PeerLanBootstrap.request(context.server(), context.player().getUUID());
            if(invite != null) ServerPlayNetworking.send(context.player(), new PeerLanPayload(false, invite));
        });
    }
}
