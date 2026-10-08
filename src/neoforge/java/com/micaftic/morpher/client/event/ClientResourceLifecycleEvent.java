package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.audio.AudioStreamCache;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.entity.EntityRenderCache;
import com.micaftic.morpher.client.upload.CloudUploadRuntime;
import com.micaftic.morpher.core.gpu.BlurStack;
import com.micaftic.morpher.core.gpu.GpuRenderPath;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

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

    @SubscribeEvent
    public static void onGameShuttingDown(GameShuttingDownEvent event) {
        EntityRenderCache.clear();
        ClientModelManager.onClientStopping();
        cleanup("client stopping");
        CloudUploadRuntime.clear();
    }

    private static void cleanup(String reason) {
        ClientModelManager.releaseServerSyncedModels(reason);
        GpuRenderPath.disposeAllMeshes(reason);
        AudioStreamCache.clearAll(reason);
        BlurStack.disposeAll(reason);
    }
}
