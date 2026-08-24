package com.micaftic.morpher.core.gui.components;

import net.minecraft.client.gui.GuiGraphics;
import com.micaftic.morpher.core.gui.Option;
import com.micaftic.morpher.core.gui.OptionRow;

public class BooleanOptionRow extends OptionRow<Boolean> {
    public BooleanOptionRow(int x, int y, int width, int height, Option<Boolean> option) {
        super(x, y, width, height, option);
    }

    @Override
    protected void renderControl(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int size = Math.min(controlHeight(), 14);
        int cx = controlX() + controlWidth() - size;
        int cy = controlY() + (controlHeight() - size) / 2;
        boolean value = option.get();
        boolean hover = isMouseOverControl(mouseX, mouseY);

        g.fill(cx, cy, cx + size, cy + size, blendBg(hover, 0xFF1A1A1A));
        g.renderOutline(cx, cy, size, size, -1);
        if (value) {
            g.fill(cx + 3, cy + 3, cx + size - 3, cy + size - 3, -1);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (isMouseOverControl(mouseX, mouseY)) {
            option.setPending(!option.get());
        }
    }
}
