package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.config.ExtraPlayerRenderConfig;
import com.micaftic.morpher.client.gui.ClassicHudLayoutScreen;
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
        if ((localPlayer = (minecraft = Minecraft.getInstance()).player) == null || minecraft.screen instanceof ClassicHudLayoutScreen) {
            return;
        }
        if (ExtraPlayerRenderConfig.ENABLE_MODERN_HUD_RENDER.get()
                && ModernHudRenderer.render(guiGraphics, localPlayer, partialTick, screenWidth, screenHeight)) {
            return;
        }
        if (ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER.get()) return;
        ModelPreviewRenderer.renderPlayerOverlay(guiGraphics, localPlayer, ExtraPlayerRenderConfig.PLAYER_POS_X.get(), ExtraPlayerRenderConfig.PLAYER_POS_Y.get(), ExtraPlayerRenderConfig.PLAYER_SCALE.get().floatValue(), ExtraPlayerRenderConfig.PLAYER_YAW_OFFSET.get().floatValue(), -500, partialTick);
    }
}
