package com.micaftic.morpher.core.architectury.event.events.client;

import com.micaftic.morpher.core.architectury.event.Event;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

/**
 * MC 26.x: Fabric-backed ClientLifecycleEvent.
 */
public class ClientLifecycleEvent {
    public static final Event<Consumer<Minecraft>> CLIENT_STARTED = new Event<>();

    static {
        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
            CLIENT_STARTED.fire(h -> h.accept((Minecraft) client))
        );
    }
}
