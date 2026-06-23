package com.micaftic.morpher.core.gui.components;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import com.micaftic.morpher.core.gui.OptionRow;

public final class LabelValueRow extends OptionRow<Object> {
    private final String labelKey;
    private final String value;

    public LabelValueRow(String labelKey, String value) {
        super(0, 0, 0, 18, null);
        this.labelKey = labelKey;
        this.value = value;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor g = extractor;
        g.fill(getX(), getY(), getX() + width, getY() + height, 0x90000000);
        Component label = Component.translatable(labelKey).withStyle(ChatFormatting.AQUA);
        g.text(Minecraft.getInstance().font, label, getX() + 8, getY() + (height - 8) / 2, -1, false);
        int labelW = Minecraft.getInstance().font.width(label);
        g.text(Minecraft.getInstance().font, Component.literal(value), getX() + 8 + labelW + 6, getY() + (height - 8) / 2, 0xFFCCCCCC, false);
    }

    @Override
    protected void renderControl(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    }
}
