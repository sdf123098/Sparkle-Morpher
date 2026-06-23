package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.network.NetworkHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.micaftic.morpher.core.api.capability.CapabilityLifecycle;

@EventBusSubscriber(modid = YesSteveModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientPlayerCloneEvent {
    private ClientPlayerCloneEvent() {}
    @SubscribeEvent public static void onClone(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.Clone event) {
        if (!YesSteveModel.isAvailable() || !NetworkHandler.isClientConnected()) return;
        CapabilityLifecycle.revive(event.getOldPlayer());
        PlayerCapability.get(event.getOldPlayer()).ifPresent(c -> PlayerCapability.get(event.getNewPlayer()).ifPresent(n -> n.copyFrom(c)));
        CapabilityLifecycle.invalidate(event.getOldPlayer());
    }
}