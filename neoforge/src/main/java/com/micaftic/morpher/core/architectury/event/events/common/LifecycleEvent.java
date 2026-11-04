package com.micaftic.morpher.core.architectury.event.events.common;

import com.micaftic.morpher.core.architectury.event.Event;
import com.micaftic.morpher.core.architectury.utils.GameInstance;
import net.minecraft.server.MinecraftServer;

import java.util.function.Consumer;

/**
 * NeoForge-compatible subset of Architectury's common lifecycle events.
 */
public class LifecycleEvent {
    public static final Event<Consumer<MinecraftServer>> SERVER_BEFORE_START = new Event<>();
    public static final Event<Runnable> SETUP = new Event<>();

    private static boolean setupFired = false;

    public static void fireServerBeforeStart(MinecraftServer server) {
        GameInstance.setServer(server);
        SERVER_BEFORE_START.fire(handler -> handler.accept(server));
    }

    public static void fireServerStopped() {
        GameInstance.setServer(null);
    }

    public static void fireSetup() {
        if (setupFired) {
            return;
        }
        setupFired = true;
        SETUP.fire(Runnable::run);
    }
}
