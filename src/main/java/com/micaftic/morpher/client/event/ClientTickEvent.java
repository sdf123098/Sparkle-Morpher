package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.audio.ObjectPool;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.gui.resource.ResourceDownloadManager;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.client.upload.ModelUploadSession;
import com.micaftic.morpher.client.upload.UploadManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import com.micaftic.morpher.core.api.PlatformAPI;

public final class ClientTickEvent {

    private static int tickCount;

    private static int refreshRate = 60;

    private ClientTickEvent() {
    }

    public static void register() {
        com.micaftic.morpher.core.architectury.event.events.client.ClientTickEvent.CLIENT_PRE.register(ClientTickEvent::onClientPreTick);
    }

    private static void onClientPreTick(Minecraft client) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        tickCount++;
        InputStateKey.tick();
        UploadManager.processPendingUploads();
        ResourceDownloadManager.tick();
        ModelUploadSession.tickCurrent();
        ClientModelManager.flushPendingModels();
        if ((tickCount & 63) == 0) {
            ClientModelManager.trimUnusedGpuCaches();
        }
        ObjectPool.cleanup();
        refreshRate = Math.max(60, client.getWindow().getRefreshRate());
        LocalPlayer localPlayer = client.player;
        if (localPlayer != null) {
            PlayerCapability.get(localPlayer).ifPresent(cap -> cap.tickAnimations());
        }
        // 在无模组服务器上，自动恢复之前持久化的模型选择
        ClientModelManager.restorePersistedModelSelectionOnVanillaServer();
    }

    public static int getTickCount() {
        return tickCount;
    }

    public static int getRefreshRate() {
        return refreshRate;
    }
}
