package com.micaftic.morpher.neoforge;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.gui.ModernPlayerModelScreen;
import com.micaftic.morpher.client.renderer.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import com.micaftic.morpher.core.api.client.HudOverlay;

@Mod(value = com.micaftic.morpher.YesSteveModel.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = com.micaftic.morpher.YesSteveModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class YesSteveModelNeoForgeClient {
    private static HudOverlay debugOverlay; private static HudOverlay loadingOverlay; private static HudOverlay syncOverlay;

    public YesSteveModelNeoForgeClient(ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (mc, parentScreen) -> ModernPlayerModelScreen.settings(parentScreen));
    }

    @SubscribeEvent public static void onClientSetup(FMLClientSetupEvent event) {
        debugOverlay = AnimationDebugOverlay.createOverlay(); loadingOverlay = new ExtraPlayerOverlay(); syncOverlay = new ModelSyncStateOverlay();
        ClientModelManager.loadDefaultModel();
        ClientModelManager.reloadLocalModels(null);
    }

    public static void sendUnavailableMessage() {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(com.micaftic.morpher.YesSteveModel.getUnavailableComponent());
        }
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
