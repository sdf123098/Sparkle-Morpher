package com.micaftic.morpher.core.gui.components;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import com.micaftic.morpher.core.gui.ModernModelInfoScreen;
import com.micaftic.morpher.core.gui.OptionRow;

public final class LinkRow extends OptionRow<Object> {
    private final ModernModelInfoScreen owner;
    private final String label;
    private final String url;

    public LinkRow(ModernModelInfoScreen owner, String label, String url) {
        super(0, 0, 0, 20, null);
        this.owner = owner;
        this.label = label;
        this.url = url;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor g = extractor;
        boolean hover = isHovered();
        g.fill(getX(), getY(), getX() + width, getY() + height, hover ? 0x90171717 : 0x90000000);
        Font font = Minecraft.getInstance().font;
        String i18nKey = "gui.sparkle_morpher.url." + label;
        Component nameComponent = I18n.exists(i18nKey) ? Component.translatable(i18nKey) : Component.literal(label);
        g.text(font, nameComponent.copy().withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE), getX() + 8, getY() + (height - 8) / 2, -1, false);
        Component urlComp = Component.literal(url).withStyle(ChatFormatting.GRAY);
        int urlW = font.width(urlComp);
        int urlX = Math.max(getX() + 8 + font.width(nameComponent) + 12, getX() + width - urlW - 8);
        g.text(font, urlComp, urlX, getY() + (height - 8) / 2, -1, false);
    }

    @Override
    protected void renderControl(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean flag) {
        owner.openUrlWithConfirm(url);
    }
}
