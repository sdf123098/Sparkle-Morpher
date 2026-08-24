package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.client.gui.HudLayoutScreen;
import com.micaftic.morpher.client.renderer.modernhud.ModernHudRenderer;
import com.micaftic.morpher.config.ExtraPlayerRenderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import com.micaftic.morpher.core.api.client.HudOverlay;

public class ExtraPlayerOverlay implements HudOverlay {
    @Override
    public void render(GuiGraphicsExtractor guiGraphics, Font font, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft;
        LocalPlayer localPlayer;
        if ((localPlayer = (minecraft = Minecraft.getInstance()).player) == null
                || com.micaftic.morpher.util.InputUtil.getCurrentScreen() instanceof HudLayoutScreen) {
            return;
        }
        if (ExtraPlayerRenderConfig.ENABLE_MODERN_HUD_RENDER.get()
                && ModernHudRenderer.render(guiGraphics, localPlayer, partialTick, screenWidth, screenHeight)) {
            return;
        }
        if (ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER.get()) {
            return;
        }
        ModelPreviewRenderer.renderPlayerOverlay(guiGraphics, localPlayer, ExtraPlayerRenderConfig.CLASSIC_HUD_LAYOUT.getX(), ExtraPlayerRenderConfig.CLASSIC_HUD_LAYOUT.getY(), ExtraPlayerRenderConfig.CLASSIC_HUD_LAYOUT.getScale(), ExtraPlayerRenderConfig.CLASSIC_HUD_LAYOUT.getYaw(), -500, partialTick, false);
    }
}
