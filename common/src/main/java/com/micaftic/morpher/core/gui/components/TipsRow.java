package com.micaftic.morpher.core.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import com.micaftic.morpher.core.gui.OptionRow;

import java.util.List;

public final class TipsRow extends OptionRow<Object> {
    private final String text;
    private List<FormattedCharSequence> cachedLines;
    private int cachedWidth = -1;

    public TipsRow(String text) {
        super(0, 0, 0, 0, null);
        this.text = text;
    }

    private void recomputeLines() {
        if (cachedWidth == width && cachedLines != null) return;
        Font font = Minecraft.getInstance().font;
        cachedLines = font.split(Component.literal(text), Math.max(20, width - 16));
        cachedWidth = width;
        this.height = Math.max(18, cachedLines.size() * 10 + 8);
    }

    @Override
    public void setWidth(int w) {
        super.setWidth(w);
        cachedLines = null;
        cachedWidth = -1;
        recomputeLines();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        recomputeLines();
        g.fill(getX(), getY(), getX() + width, getY() + height, 0x90000000);
        Font font = Minecraft.getInstance().font;
        int y = getY() + 4;
        for (FormattedCharSequence line : cachedLines) {
            g.drawString(font, line, getX() + 8, y, 0xFFEEEEEE, false);
            y += 10;
        }
    }

    @Override
    protected void renderControl(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }
}
