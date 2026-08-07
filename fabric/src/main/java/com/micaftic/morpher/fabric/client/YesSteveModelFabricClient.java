package com.micaftic.morpher.fabric.client;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.compat.ClientRenderCompatibility;
import com.micaftic.morpher.client.compat.ClientRenderCompatibilityRegistry;
import com.micaftic.morpher.client.renderer.AnimationDebugOverlay;
import com.micaftic.morpher.client.renderer.ExtraPlayerOverlay;
import com.micaftic.morpher.client.renderer.ModelSyncStateOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import com.micaftic.morpher.core.api.client.HudOverlay;

public final class YesSteveModelFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        net.fabricmc.loader.api.FabricLoader.getInstance()
                .getEntrypoints("sparkle_morpher_render_compat", ClientRenderCompatibility.class)
                .forEach(ClientRenderCompatibilityRegistry::register);
        OrihimeDirectModelCompat.init();
        FabricClientResourceLifecycle.register();
        HudOverlay debugOverlay = AnimationDebugOverlay.createOverlay();
        HudOverlay loadingOverlay = new ExtraPlayerOverlay();
        HudOverlay syncOverlay = new ModelSyncStateOverlay();
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            float delta = tickDelta.getGameTimeDeltaPartialTick(false);
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            debugOverlay.render(guiGraphics, mc.font, delta, w, h);
            loadingOverlay.render(guiGraphics, mc.font, delta, w, h);
            syncOverlay.render(guiGraphics, mc.font, delta, w, h);
        });

        ClientModelManager.loadDefaultModel();
        ClientModelManager.reloadLocalModels(null);
    }
}
