package com.micaftic.morpher.client.event;

import com.micaftic.morpher.audio.AudioStreamCache;
import com.micaftic.morpher.core.gpu.BlurStack;
import com.micaftic.morpher.core.architectury.event.events.client.ClientLifecycleEvent;
import com.micaftic.morpher.core.architectury.event.events.client.ClientPlayerEvent;
import com.micaftic.morpher.core.gpu.GpuRenderPath;
import com.micaftic.morpher.capability.fabric.client.PlayerCapabilityClientStore;
import com.micaftic.morpher.capability.fabric.client.ProjectileCapabilityClientStore;
import com.micaftic.morpher.capability.fabric.client.VehicleCapabilityClientStore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;

public final class ClientResourceLifecycleEvent {
    private ClientResourceLifecycleEvent() {
    }

    public static void register() {
        ClientPlayerEvent.CLIENT_DISCONNECT.register(_ -> cleanup("client disconnect"));
        ClientLifecycleEvent.CLIENT_STOPPING.register(_ -> cleanup("client stopping"));
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((_, _) -> cleanupEntityCaps("client level changed"));
    }

    private static void cleanup(String reason) {
        GpuRenderPath.disposeAllMeshes(reason);
        AudioStreamCache.clearAll(reason);
        BlurStack.disposeAll(reason);
        PlayerCapabilityClientStore.clear(reason);
        ProjectileCapabilityClientStore.clear(reason);
        VehicleCapabilityClientStore.clear(reason);
    }

    private static void cleanupEntityCaps(String reason) {
        PlayerCapabilityClientStore.clear(reason);
        ProjectileCapabilityClientStore.clear(reason);
        VehicleCapabilityClientStore.clear(reason);
    }
}
