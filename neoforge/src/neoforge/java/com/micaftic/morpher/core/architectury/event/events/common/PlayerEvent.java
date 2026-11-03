package com.micaftic.morpher.core.architectury.event.events.common;

import com.micaftic.morpher.core.architectury.event.Event;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * NeoForge-compatible subset of Architectury's player events.
 */
public class PlayerEvent {
    public static final Event<Consumer<ServerPlayer>> PLAYER_QUIT = new Event<>();
    public static final Event<Consumer<ServerPlayer>> PLAYER_JOIN = new Event<>();
    public static final Event<PlayerClone> PLAYER_CLONE = new Event<>();

    public static void fireJoin(ServerPlayer player) {
        PLAYER_JOIN.fire(handler -> handler.accept(player));
    }

    public static void fireQuit(ServerPlayer player) {
        PLAYER_QUIT.fire(handler -> handler.accept(player));
    }

    public static void fireClone(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean wasDeath) {
        PLAYER_CLONE.fire(handler -> handler.clone(oldPlayer, newPlayer, wasDeath));
    }

    @FunctionalInterface
    public interface PlayerClone {
        void clone(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean wasDeath);
    }
}
