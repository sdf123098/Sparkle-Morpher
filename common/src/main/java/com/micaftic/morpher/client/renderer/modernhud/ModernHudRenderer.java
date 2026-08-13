package com.micaftic.morpher.client.renderer.modernhud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

/** Stable entry contract for the shared-pose modern HUD pipeline. */
public final class ModernHudRenderer {
    private ModernHudRenderer() {
    }

    /** Returns true only after the modern renderer has fully drawn and composited this frame. */
    public static boolean render(GuiGraphics graphics, LocalPlayer player, float partialTick,
                                 int screenWidth, int screenHeight) {
        return false;
    }
}
