package com.micaftic.morpher.client.event;

import com.micaftic.morpher.audio.AudioStreamCache;
import com.micaftic.morpher.capability.client.PlayerCapabilityClientStore;
import com.micaftic.morpher.core.architectury.event.events.client.ClientPlayerEvent;
import com.micaftic.morpher.core.gpu.BlurStack;
import com.micaftic.morpher.core.gpu.GpuRenderPath;

public final class ClientResourceLifecycleEvent {
    private ClientResourceLifecycleEvent() {
    }

    public static void register() {
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> cleanup("client disconnect"));
    }

    private static void cleanup(String reason) {
        GpuRenderPath.disposeAllMeshes(reason);
        AudioStreamCache.clearAll(reason);
        BlurStack.disposeAll(reason);
        PlayerCapabilityClientStore.clear();
    }
}
