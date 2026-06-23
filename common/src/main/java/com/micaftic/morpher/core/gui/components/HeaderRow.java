package com.micaftic.morpher.core.gui.components;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import com.micaftic.morpher.core.gui.OptionRow;

public final class HeaderRow extends OptionRow<Object> {
    private final String text;

    public HeaderRow(String text) {
        super(0, 0, 0, 22, null);
        this.text = text;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor g = extractor;
        g.fill(getX(), getY(), getX() + width, getY() + height, 0x90000000);
        g.text(Minecraft.getInstance().font, Component.literal(text).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), getX() + 8, getY() + (height - 8) / 2, -1, false);
    }

    @Override
    protected void renderControl(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    }
}
