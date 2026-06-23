package com.micaftic.morpher.core.api.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

@FunctionalInterface
public interface HudOverlay {
    void render(GuiGraphics guiGraphics, Font font, float partialTick, int screenWidth, int screenHeight);
}
