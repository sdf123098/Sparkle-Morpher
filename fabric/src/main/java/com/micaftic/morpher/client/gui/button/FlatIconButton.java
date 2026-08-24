package com.micaftic.morpher.client.gui.button;

import com.micaftic.morpher.client.gui.ISpecialWidget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;


@Environment(EnvType.CLIENT)
public class FlatIconButton extends AbstractWidget implements ISpecialWidget {

    private final int iconIndex;

    public FlatIconButton(int x, int y, int iconIndex, Component component) {
        super(x, y, 115, 15, component);
        this.iconIndex = iconIndex;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor guiGraphics = extractor;
        Font font = Minecraft.getInstance().font;
        guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), -280804798);
        Component message = getMessage();
        int textWidth = font.width(message);
        int textX = textWidth <= getWidth() - 4 ? getX() + (getWidth() - textWidth) / 2 : getX() + 2;
        int textY = getY() + (getHeight() - 8) / 2;
        guiGraphics.enableScissor(getX() + 2, getY(), getX() + getWidth() - 2, getY() + getHeight());
        guiGraphics.text(font, message, textX, textY, 16777215, true);
        guiGraphics.disableScissor();
    }

    public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
