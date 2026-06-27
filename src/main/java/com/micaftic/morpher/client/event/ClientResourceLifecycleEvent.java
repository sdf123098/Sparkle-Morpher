package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.audio.AudioStreamCache;
import com.micaftic.morpher.core.gpu.BlurStack;
import com.micaftic.morpher.core.gpu.GpuRenderPath;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = YesSteveModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientResourceLifecycleEvent {
    private ClientResourceLifecycleEvent() {
    }

    public static void register() {
    }

    @SubscribeEvent
    public static void onDisconnect(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        cleanup("client disconnect");
    }

    private static void cleanup(String reason) {
        GpuRenderPath.disposeAllMeshes(reason);
        AudioStreamCache.clearAll(reason);
        BlurStack.disposeAll(reason);
    }
}
