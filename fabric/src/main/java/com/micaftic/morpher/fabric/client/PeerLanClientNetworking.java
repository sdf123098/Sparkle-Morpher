package com.micaftic.morpher.fabric.client;

import com.micaftic.morpher.client.peer.ClientPeerRuntime;
import com.micaftic.morpher.network.message.PeerLanPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class PeerLanClientNetworking {
    private PeerLanClientNetworking() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PeerLanPayload.TYPE, (payload, context) -> {
            var connection = context.client().getConnection();
            if(!payload.request()) context.client().execute(() -> ClientPeerRuntime.acceptAutomaticOffer(connection, payload.invitation()));
        });
        ClientPeerRuntime.configureBootstrap(
                () -> Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(PeerLanPayload.TYPE),
                () -> ClientPlayNetworking.send(new PeerLanPayload(true, "")));
    }
}
