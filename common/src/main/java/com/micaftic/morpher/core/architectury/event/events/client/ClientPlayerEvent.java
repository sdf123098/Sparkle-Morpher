package com.micaftic.morpher.core.architectury.event.events.client;

import com.micaftic.morpher.core.architectury.event.Event;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * MC 26.x: Fabric-backed ClientPlayerEvent.
 */
public class ClientPlayerEvent {
    public static final Event<Consumer<LocalPlayer>> CLIENT_PLAYER_JOIN = new Event<>();
    public static final Event<Consumer<LocalPlayer>> CLIENT_PLAYER_QUIT = new Event<>();
    public static final Event<BiConsumer<LocalPlayer, LocalPlayer>> CLIENT_PLAYER_RESPAWN = new Event<>();

    static {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.player != null) {
                CLIENT_PLAYER_JOIN.fire(h -> h.accept(client.player));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (client.player != null) {
                CLIENT_PLAYER_QUIT.fire(h -> h.accept(client.player));
            }
        });
    }
}
