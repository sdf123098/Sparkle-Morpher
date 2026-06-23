package com.micaftic.morpher.core.architectury.event.events.client;

import com.micaftic.morpher.core.architectury.event.Event;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

/**
 * MC 26.x: NeoForge-compatible ClientTickEvent.
 */
public class ClientTickEvent {
    public static final Event<Consumer<Minecraft>> CLIENT_PRE = new Event<>();
    public static final Event<Consumer<Minecraft>> CLIENT_POST = new Event<>();

    public static void fireClientPre(Minecraft client) {
        CLIENT_PRE.fire(handler -> handler.accept(client));
    }

    public static void fireClientPost(Minecraft client) {
        CLIENT_POST.fire(handler -> handler.accept(client));
    }
}
