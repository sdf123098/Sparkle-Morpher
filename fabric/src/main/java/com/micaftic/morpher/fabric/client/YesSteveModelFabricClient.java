package com.micaftic.morpher.fabric.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.renderer.AnimationDebugOverlay;
import com.micaftic.morpher.client.renderer.ExtraPlayerOverlay;
import com.micaftic.morpher.client.renderer.ModelSyncStateOverlay;
import com.micaftic.morpher.core.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;
import com.micaftic.morpher.core.api.client.HudOverlay;

public final class YesSteveModelFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyMappingRegistry.getCustomKeyMappings().forEach(KeyMappingHelper::registerKeyMapping);

        HudOverlay debugOverlay = AnimationDebugOverlay.createOverlay();
        HudOverlay loadingOverlay = new ExtraPlayerOverlay();
        HudOverlay syncOverlay = new ModelSyncStateOverlay();
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "hud_overlays"), (guiGraphics, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            float delta = tickDelta.getGameTimeDeltaTicks();
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            Font font = mc.font;
            debugOverlay.render(guiGraphics, font, delta, w, h);
            loadingOverlay.render(guiGraphics, font, delta, w, h);
            syncOverlay.render(guiGraphics, font, delta, w, h);
        });

        ClientModelManager.loadDefaultModel();
        ClientModelManager.reloadLocalModels(null, false);
    }
}
