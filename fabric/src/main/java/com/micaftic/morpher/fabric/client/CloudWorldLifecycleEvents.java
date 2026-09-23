package com.micaftic.morpher.fabric.client;

import com.micaftic.morpher.cloud.client.CloudClientRuntime;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Bridges Fabric connection events to the loader-neutral Cloud world lifecycle. */
final class CloudWorldLifecycleEvents {
    private CloudWorldLifecycleEvents() {}

    static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                CloudClientRuntime.onWorldJoined(handler));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                CloudClientRuntime.onWorldLeft(handler));
    }
}
