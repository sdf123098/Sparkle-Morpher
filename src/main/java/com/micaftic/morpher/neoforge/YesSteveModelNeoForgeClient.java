package com.micaftic.morpher.neoforge;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.renderer.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import com.micaftic.morpher.core.api.client.HudOverlay;

@EventBusSubscriber(modid = com.micaftic.morpher.YesSteveModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class YesSteveModelNeoForgeClient {
    private static HudOverlay debugOverlay; private static HudOverlay loadingOverlay; private static HudOverlay syncOverlay;

    @SubscribeEvent public static void onClientSetup(FMLClientSetupEvent event) {
        debugOverlay = AnimationDebugOverlay.createOverlay(); loadingOverlay = new ExtraPlayerOverlay(); syncOverlay = new ModelSyncStateOverlay();
        ClientModelManager.loadDefaultModel();
    }

    @EventBusSubscriber(modid = com.micaftic.morpher.YesSteveModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
    public static class GameBus {
        @SubscribeEvent public static void onRenderGui(RenderGuiLayerEvent.Post event) {
            Minecraft mc = Minecraft.getInstance(); float delta = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            int w = mc.getWindow().getGuiScaledWidth(); int h = mc.getWindow().getGuiScaledHeight();
            debugOverlay.render(event.getGuiGraphics(), mc.font, delta, w, h);
            loadingOverlay.render(event.getGuiGraphics(), mc.font, delta, w, h);
            syncOverlay.render(event.getGuiGraphics(), mc.font, delta, w, h);
        }
    }
}