package com.micaftic.morpher.core.architectury.event.events.client;

import com.micaftic.morpher.core.architectury.event.Event;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

/**
 * MC 26.x: Fabric-backed ClientTickEvent.
 */
public class ClientTickEvent {
    public static final Event<Consumer<Minecraft>> CLIENT_PRE = new Event<>();
    public static final Event<Consumer<Minecraft>> CLIENT_POST = new Event<>();

    static {
        ClientTickEvents.START_CLIENT_TICK.register(client ->
            CLIENT_PRE.fire(h -> h.accept((Minecraft) client))
        );
        ClientTickEvents.END_CLIENT_TICK.register(client ->
            CLIENT_POST.fire(h -> h.accept((Minecraft) client))
        );
    }
}
