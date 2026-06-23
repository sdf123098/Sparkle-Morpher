package com.micaftic.morpher.core.architectury.event.events.common;

import com.micaftic.morpher.core.architectury.event.Event;
import com.micaftic.morpher.core.architectury.utils.GameInstance;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.util.function.Consumer;

/**
 * Fabric-backed subset of Architectury's common lifecycle events.
 */
public class LifecycleEvent {
    public static final Event<Consumer<MinecraftServer>> SERVER_BEFORE_START = new Event<>();
    public static final Event<Runnable> SETUP = new Event<>();

    private static boolean setupFired = false;

    static {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            GameInstance.setServer(server);
            SERVER_BEFORE_START.fire(handler -> handler.accept(server));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> GameInstance.setServer(null));
    }

    public static void fireSetup() {
        if (setupFired) {
            return;
        }
        setupFired = true;
        SETUP.fire(Runnable::run);
    }
}
