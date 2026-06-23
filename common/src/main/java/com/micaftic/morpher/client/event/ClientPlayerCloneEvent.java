package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.core.architectury.event.events.client.ClientPlayerEvent;
import net.minecraft.client.player.LocalPlayer;
import com.micaftic.morpher.core.api.PlatformAPI;
import com.micaftic.morpher.core.api.capability.CapabilityLifecycle;

public final class ClientPlayerCloneEvent {

    private ClientPlayerCloneEvent() {
    }

    public static void register() {
        ClientPlayerEvent.CLIENT_PLAYER_RESPAWN.register(ClientPlayerCloneEvent::onClientPlayerRespawn);
    }

    private static void onClientPlayerRespawn(LocalPlayer oldPlayer, LocalPlayer newPlayer) {
        if (!YesSteveModel.isAvailable() || !NetworkHandler.isClientConnected()) {
            return;
        }
        CapabilityLifecycle.revive(oldPlayer);
        PlayerCapability.get(oldPlayer).ifPresent(cap -> PlayerCapability.get(newPlayer).ifPresent(cap2 -> cap2.copyFrom(cap)));
        CapabilityLifecycle.invalidate(oldPlayer);
    }
}