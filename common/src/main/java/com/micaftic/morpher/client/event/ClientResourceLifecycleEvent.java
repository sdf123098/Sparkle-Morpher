package com.micaftic.morpher.client.event;

import com.micaftic.morpher.audio.AudioStreamCache;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.core.gpu.BlurStack;
import com.micaftic.morpher.core.gpu.GpuRenderPath;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;

public final class ClientResourceLifecycleEvent {
    private ClientResourceLifecycleEvent() {
    }

    public static void register() {
        // 能力存储（CapabilityClientStore）的清理由各自平台模块处理（fabric 在 FabricClientResourceLifecycle 中注册）。
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> cleanup("client disconnect"));
        ClientLifecycleEvent.CLIENT_STOPPING.register(client -> cleanup("client stopping"));
    }

    private static void cleanup(String reason) {
        ClientModelManager.releaseServerSyncedModels(reason);
        GpuRenderPath.disposeAllMeshes(reason);
        AudioStreamCache.clearAll(reason);
        BlurStack.disposeAll(reason);
    }
}