package com.micaftic.morpher.neoforge.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.cloud.client.CloudClientRuntime;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Bridges NeoForge connection events to the loader-neutral Cloud world lifecycle. */
@EventBusSubscriber(modid = YesSteveModel.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class CloudWorldLifecycleEvents {
    private CloudWorldLifecycleEvents() {}

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        LocalPlayer player = event.getPlayer();
        if (player != null) CloudClientRuntime.onWorldJoined(player.connection);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        LocalPlayer player = event.getPlayer();
        if (player == null) CloudClientRuntime.onWorldDisconnected();
        else CloudClientRuntime.onWorldLeft(player.connection);
    }
}
