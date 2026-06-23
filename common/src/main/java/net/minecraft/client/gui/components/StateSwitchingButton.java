package net.minecraft.client.gui.components;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.WidgetSprites;
import net.minecraft.network.chat.Component;

public abstract class StateSwitchingButton implements Renderable, GuiEventListener, NarratableEntry {
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean isStateTriggered;
    protected boolean isHovered;
    protected Component message;
    protected net.minecraft.client.gui.components.Tooltip tooltip;

    public StateSwitchingButton(int x, int y, int width, int height, boolean initialState) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.isStateTriggered = initialState;
    }

    public void initTextureValues(WidgetSprites sprites) {}

    public boolean isHovered() { return isHovered; }
    public void setHovered(boolean hovered) { this.isHovered = hovered; }

    public int getX() { return x; }
    public int getY() { return y; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public void setWidth(int w) { this.width = w; }
    public void setHeight(int h) { this.height = h; }

    public boolean isStateTriggered() { return isStateTriggered; }
    public void setStateTriggered(boolean triggered) { this.isStateTriggered = triggered; }

    public void setTooltip(net.minecraft.client.gui.components.Tooltip tooltip) { this.tooltip = tooltip; }
    public net.minecraft.client.gui.components.Tooltip getTooltip() { return tooltip; }

    public Component getMessage() { return message; }
    public void setMessage(Component message) { this.message = message; }

    // Rendering
    public void renderWidget(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {}
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {}

    // GuiEventListener
    @Override
    public void setFocused(boolean focused) {}
    @Override
    public boolean isFocused() { return false; }

    // NarratableEntry
    @Override
    public NarratableEntry.NarrationPriority narrationPriority() { return NarratableEntry.NarrationPriority.NONE; }
    @Override
    public void updateNarration(NarrationElementOutput output) {}
}
