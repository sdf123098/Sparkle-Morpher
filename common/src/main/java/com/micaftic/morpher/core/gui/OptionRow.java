package com.micaftic.morpher.core.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public abstract class OptionRow<T> extends AbstractWidget {
    protected final Option<T> option;

    protected OptionRow(int x, int y, int width, int height, Option<T> option) {
        super(x, y, width, height, option == null ? Component.empty() : option.getLabel());
        this.option = option;
    }

    public Option<T> getOption() {
        return option;
    }

    public void refresh() {
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor g = extractor;
        boolean dirty = option != null && option.isDirty();
        int bg = isHovered() ? 0x90171717 : (dirty ? 0x90060606 : 0x90000000);
        g.fill(getX(), getY(), getX() + width, getY() + height, bg);

        Component label = getMessage();
        int textColor = dirty ? -1 : 0x90FFFFFF;
        int textY = getY() + (height - 8) / 2;
        g.text(Minecraft.getInstance().font, label, getX() + 8, textY, textColor, false);

        renderControl(g, mouseX, mouseY, partialTick);
    }

    protected abstract void renderControl(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick);

    protected int controlX() {
        return getX() + width - controlWidth() - 6;
    }

    protected int controlY() {
        return getY() + (height - controlHeight()) / 2;
    }

    protected int controlWidth() {
        return 90;
    }

    protected int controlHeight() {
        return Math.min(height - 4, 16);
    }

    protected boolean isMouseOverControl(double mx, double my) {
        int cx = controlX();
        int cy = controlY();
        return mx >= cx && mx < cx + controlWidth() && my >= cy && my < cy + controlHeight();
    }

    public boolean isOverlayOpen() {
        return false;
    }

    public void closeOverlay() {
    }

    public void renderOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, float scrollDisplay) {
    }

    public boolean overlayMouseClicked(double mouseX, double mouseY, int button, float scrollDisplay) {
        return false;
    }

    public boolean overlayMouseScrolled(double mouseX, double mouseY, double delta, float scrollDisplay) {
        return false;
    }

    protected static int blendBg(boolean hover, int base) {
        if (!hover) return base;
        int a = (base >>> 24) & 0xFF;
        int r = Mth.clamp(((base >> 16) & 0xFF) + 40, 0, 255);
        int gn = Mth.clamp(((base >> 8) & 0xFF) + 40, 0, 255);
        int b = Mth.clamp((base & 0xFF) + 40, 0, 255);
        return (a << 24) | (r << 16) | (gn << 8) | b;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
