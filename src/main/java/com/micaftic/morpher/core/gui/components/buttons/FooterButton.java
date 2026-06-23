package com.micaftic.morpher.core.gui.components.buttons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.awt.*;

public class FooterButton extends AbstractWidget {
    private final Runnable onPress;

    public FooterButton(int x, int y, int width, int height, Component label, Runnable onPress) {
        super(x, y, width, height, label);
        this.onPress = onPress;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int bg = !active ? 0x90282828 : (isHovered() ? new Color(0x90171717, true).getRGB() : 0x90000000);
        g.fill(getX(), getY(), getX() + width, getY() + height, bg);
        int tw = Minecraft.getInstance().font.width(getMessage());
        int color = active ? 0xFFFFFFFF : 0xFF888888;
        g.drawString(Minecraft.getInstance().font, getMessage(), getX() + (width - tw) / 2, getY() + (height - 8) / 2, color, false);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (active) onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
