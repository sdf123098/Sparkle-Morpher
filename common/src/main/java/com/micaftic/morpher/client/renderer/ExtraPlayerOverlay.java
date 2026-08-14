package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.config.ExtraPlayerRenderConfig;
import com.micaftic.morpher.config.HudLayoutConfig;
import com.micaftic.morpher.client.gui.HudLayoutScreen;
import com.micaftic.morpher.client.renderer.modernhud.ModernHudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import com.micaftic.morpher.core.api.client.HudOverlay;

public class ExtraPlayerOverlay implements HudOverlay {
    @Override
    public void render(GuiGraphics guiGraphics, Font font, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft;
        LocalPlayer localPlayer;
        if ((localPlayer = (minecraft = Minecraft.getInstance()).player) == null || minecraft.screen instanceof HudLayoutScreen) {
            return;
        }
        if (ExtraPlayerRenderConfig.ENABLE_MODERN_HUD_RENDER.get()
                && ModernHudRenderer.render(guiGraphics, localPlayer, partialTick, screenWidth, screenHeight)) {
            return;
        }
        if (ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER.get()) return;
        HudLayoutConfig layout = ExtraPlayerRenderConfig.CLASSIC_HUD_LAYOUT;
        ModelPreviewRenderer.renderPlayerOverlay(guiGraphics, localPlayer, layout.getX(), layout.getY(), layout.getScale(), layout.getYaw(), -500, partialTick);
    }
}
