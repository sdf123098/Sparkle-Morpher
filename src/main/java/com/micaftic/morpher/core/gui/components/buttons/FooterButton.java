package com.micaftic.morpher.core.gui.components.buttons;

import com.micaftic.morpher.core.gui.RoulettePanelStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class FooterButton extends AbstractWidget {
    private final Runnable onPress;
    private final RoulettePanelStyle.Glyph glyph;

    public FooterButton(int x, int y, int width, int height, Component label, Runnable onPress) {
        this(x, y, width, height, label, onPress, RoulettePanelStyle.Glyph.CHECK);
    }

    public FooterButton(int x, int y, int width, int height, Component label, Runnable onPress, RoulettePanelStyle.Glyph glyph) {
        super(x, y, width, height, label);
        this.onPress = onPress;
        this.glyph = glyph;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        RoulettePanelStyle.iconButton(extractor, mouseX, mouseY, getX(), getY(), glyph, active);
        if (active && isHovered()) {
            int tw = Math.min(180, net.minecraft.client.Minecraft.getInstance().font.width(getMessage()) + 10);
            int tx = Math.min(mouseX + 10, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth() - tw - 4);
            int ty = Math.min(mouseY + 10, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight() - 18);
            RoulettePanelStyle.fill(extractor, tx, ty, tw, 16, 0xEE101010);
            RoulettePanelStyle.border(extractor, tx, ty, tw, 16, 0x88FFFFFF);
            extractor.text(net.minecraft.client.Minecraft.getInstance().font,
                    Component.literal(RoulettePanelStyle.trim(net.minecraft.client.Minecraft.getInstance().font, getMessage().getString(), tw - 8)),
                    tx + 5, ty + 5, 0xFFFFFFFF, false);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean flag) {
        if (active) onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
