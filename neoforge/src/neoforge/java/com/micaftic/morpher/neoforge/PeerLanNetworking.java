package com.micaftic.morpher.neoforge;

import com.micaftic.morpher.client.peer.ClientPeerRuntime;
import com.micaftic.morpher.peer.PeerLanBootstrap;
import com.micaftic.morpher.network.message.PeerLanPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class PeerLanNetworking {
    private PeerLanNetworking() {}
    public static void register(IEventBus bus) {
        bus.addListener(PeerLanNetworking::registerPayload);
        ClientPeerRuntime.configureBootstrap(
                () -> Minecraft.getInstance().getConnection() != null && Minecraft.getInstance().getConnection().hasChannel(PeerLanPayload.TYPE),
                () -> net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new PeerLanPayload(true, "")));
    }
    private static void registerPayload(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playBidirectional(PeerLanPayload.TYPE, PeerLanPayload.CODEC,
                PeerLanNetworking::serverReceive, PeerLanNetworking::clientReceive);
    }
    private static void serverReceive(PeerLanPayload payload, IPayloadContext context) {
        if(!payload.request() || !(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            String invite = PeerLanBootstrap.request(player.level().getServer(), player.getUUID());
            if(invite != null) context.reply(new PeerLanPayload(false, invite));
        });
    }
    private static void clientReceive(PeerLanPayload payload, IPayloadContext context) {
        Object connection = Minecraft.getInstance().getConnection();
        if(!payload.request()) context.enqueueWork(() -> ClientPeerRuntime.acceptAutomaticOffer(connection, payload.invitation()));
    }
}
