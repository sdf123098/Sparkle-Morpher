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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = YesSteveModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientTickEvent {

    private static int tickCount;

    private static int refreshRate = 60;

    private static final int REFRESH_RATE_UPDATE_INTERVAL_TICKS = 20;

    private static final int OBJECT_POOL_CLEANUP_INTERVAL_TICKS = 10;

    private ClientTickEvent() {
    }

    @SubscribeEvent
    public static void onTick(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) {
        Minecraft client = Minecraft.getInstance();
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        tickCount++;
        InputStateKey.tick();
        UploadManager.processPendingUploads();
        ModelUploadSession.tickCurrent();
        ClientModelManager.flushPendingModels();
        ClientModelManager.tickSyncWatchdog();
        ClientModelManager.trimUnusedGpuCaches();
        if (tickCount % OBJECT_POOL_CLEANUP_INTERVAL_TICKS == 0) {
            ObjectPool.cleanup();
        }
        if (tickCount % REFRESH_RATE_UPDATE_INTERVAL_TICKS == 0) {
            refreshRate = client.getWindow().getRefreshRate();
        }
        LocalPlayer localPlayer = client.player;
        if (localPlayer != null) {
            PlayerCapability.get(localPlayer).ifPresent(cap -> cap.tickAnimations());
        }
        ClientModelManager.restorePersistedModelSelectionOnVanillaServer();
    }

    public static int getTickCount() {
        return tickCount;
    }

    public static int getRefreshRate() {
        return refreshRate;
    }
}
