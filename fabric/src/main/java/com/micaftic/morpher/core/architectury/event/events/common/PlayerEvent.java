package com.micaftic.morpher.core.architectury.event.events.common;

import com.micaftic.morpher.core.architectury.event.Event;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * Fabric-backed subset of Architectury's player events.
 */
public class PlayerEvent {
    public static final Event<Consumer<ServerPlayer>> PLAYER_QUIT = new Event<>();
    public static final Event<Consumer<ServerPlayer>> PLAYER_JOIN = new Event<>();
    public static final Event<PlayerClone> PLAYER_CLONE = new Event<>();

    static {
        ServerPlayerEvents.JOIN.register(player -> PLAYER_JOIN.fire(handler -> handler.accept(player)));
        ServerPlayerEvents.LEAVE.register(player -> PLAYER_QUIT.fire(handler -> handler.accept(player)));
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
                PLAYER_CLONE.fire(handler -> handler.clone(oldPlayer, newPlayer, !alive)));
    }

    @FunctionalInterface
    public interface PlayerClone {
        void clone(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean wasDeath);
    }
}
