package com.micaftic.morpher.fabric.client;

import com.micaftic.morpher.capability.fabric.client.PlayerCapabilityClientStore;
import com.micaftic.morpher.capability.fabric.client.ProjectileCapabilityClientStore;
import com.micaftic.morpher.capability.fabric.client.VehicleCapabilityClientStore;
import dev.architectury.event.events.client.ClientPlayerEvent;

public final class FabricClientResourceLifecycle {
    private FabricClientResourceLifecycle() {
    }

    public static void register() {
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            String reason = "client disconnect";
            PlayerCapabilityClientStore.clear(reason);
            ProjectileCapabilityClientStore.clear(reason);
            VehicleCapabilityClientStore.clear(reason);
        });
    }
}
