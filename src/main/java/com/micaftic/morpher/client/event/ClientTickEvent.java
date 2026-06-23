package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.audio.ObjectPool;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.client.upload.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = YesSteveModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientTickEvent {
    private static int tickCount; private static int refreshRate = 60;
    private ClientTickEvent() {}
    @SubscribeEvent public static void onTick(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) {
        Minecraft c = Minecraft.getInstance(); if (!YesSteveModel.isAvailable()) return;
        tickCount++; InputStateKey.tick(); UploadManager.processPendingUploads(); ModelUploadSession.tickCurrent();
        ClientModelManager.flushPendingModels(); ClientModelManager.trimUnusedGpuCaches(); ObjectPool.cleanup();
        refreshRate = c.getWindow().getRefreshRate();
        LocalPlayer p = c.player; if (p != null) PlayerCapability.get(p).ifPresent(cap -> cap.tickAnimations());
        // 在无模组服务器上，自动恢复之前持久化的模型选择
        ClientModelManager.restorePersistedModelSelectionOnVanillaServer();
    }
    public static int getTickCount() { return tickCount; } public static int getRefreshRate() { return refreshRate; }
}