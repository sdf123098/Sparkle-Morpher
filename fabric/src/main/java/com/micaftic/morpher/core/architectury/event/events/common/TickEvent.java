package com.micaftic.morpher.core.architectury.event.events.common;

import com.micaftic.morpher.core.architectury.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.function.Consumer;

/**
 * Fabric-backed subset of Architectury's server tick events.
 */
public class TickEvent {
    public static final Event<Consumer<MinecraftServer>> SERVER_POST = new Event<>();

    static {
        ServerTickEvents.END_SERVER_TICK.register(server -> SERVER_POST.fire(handler -> handler.accept(server)));
    }
}
