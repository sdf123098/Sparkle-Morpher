package com.micaftic.morpher.core.architectury.event.events.client;

import com.micaftic.morpher.core.architectury.event.Event;
import net.minecraft.client.player.LocalPlayer;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * MC 26.x: NeoForge-compatible ClientPlayerEvent.
 */
public class ClientPlayerEvent {
    public static final Event<Consumer<LocalPlayer>> CLIENT_PLAYER_JOIN = new Event<>();
    public static final Event<Consumer<LocalPlayer>> CLIENT_PLAYER_QUIT = new Event<>();
    public static final Event<BiConsumer<LocalPlayer, LocalPlayer>> CLIENT_PLAYER_RESPAWN = new Event<>();

    public static void fireJoin(LocalPlayer player) {
        CLIENT_PLAYER_JOIN.fire(handler -> handler.accept(player));
    }

    public static void fireQuit(LocalPlayer player) {
        CLIENT_PLAYER_QUIT.fire(handler -> handler.accept(player));
    }

    public static void fireRespawn(LocalPlayer oldPlayer, LocalPlayer newPlayer) {
        CLIENT_PLAYER_RESPAWN.fire(handler -> handler.accept(oldPlayer, newPlayer));
    }
}
