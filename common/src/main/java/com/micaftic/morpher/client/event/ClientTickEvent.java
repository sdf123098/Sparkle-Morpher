package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.audio.ObjectPool;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.ClientModelManager;
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
        dev.architectury.event.events.client.ClientTickEvent.CLIENT_PRE.register(ClientTickEvent::onClientPreTick);
    }

    private static void onClientPreTick(Minecraft client) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        tickCount++;
        InputStateKey.tick();
        UploadManager.processPendingUploads();
        ModelUploadSession.tickCurrent();
        ClientModelManager.flushPendingModels();
        ClientModelManager.restorePersistedModelSelectionOnVanillaServer();
        ClientModelManager.trimUnusedGpuCaches();
        ObjectPool.cleanup();
        refreshRate = client.getWindow().getRefreshRate();
        LocalPlayer localPlayer = client.player;
        if (localPlayer != null) {
            PlayerCapability.get(localPlayer).ifPresent(cap -> cap.tickAnimations());
        }
    }

    public static int getTickCount() {
        return tickCount;
    }

    public static int getRefreshRate() {
        return refreshRate;
    }
}
