package com.micaftic.morpher.core.architectury.event.events.client;

import com.micaftic.morpher.core.architectury.event.Event;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

/**
 * MC 26.x: NeoForge-compatible ClientLifecycleEvent.
 */
public class ClientLifecycleEvent {
    public static final Event<Consumer<Minecraft>> CLIENT_STARTED = new Event<>();

    public static void fireClientStarted(Minecraft client) {
        CLIENT_STARTED.fire(handler -> handler.accept(client));
    }
}
