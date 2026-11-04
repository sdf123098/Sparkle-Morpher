package com.micaftic.morpher.client.event;

import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;

public final class NeoClientShutdownEvent {
    private NeoClientShutdownEvent() {
    }

    public static void onClientStopping(ClientStoppingEvent event) {
        ClientResourceLifecycleEvent.onClientStopping();
    }
}
