package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.config.GeneralConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Objects;

public class DisclaimerScreen extends Screen {

    private Checkbox checkbox;

    private int textY;

    private int textHeight;

    private int textWidth = 400;

    public DisclaimerScreen() {
        super(Component.literal("Disclaimer GUI"));
    }

    public void init() {
        clearWidgets();
        int colW = Math.min(this.width - 20, 400);
        int btnW = Math.min(this.width - 20, 300);
        this.textWidth = colW;
        int size = this.font.split(Component.translatable("gui.sparkle_morpher.disclaimer.text"), colW).size();
        Objects.requireNonNull(this.font);
        int i = (size * 9) + 20 + 20 + 10 + 20;
        this.textY = (this.width - colW) / 2;
        this.textHeight = (this.height - i) / 2;
        MutableComponent mutableComponentTranslatable = Component.translatable("gui.sparkle_morpher.disclaimer.read");
        int iWidth = this.font.width(mutableComponentTranslatable);
        this.checkbox = Checkbox.builder(mutableComponentTranslatable, font).pos((this.width - iWidth) / 2, (this.textHeight + i) - 50).maxWidth(iWidth).selected(!GeneralConfig.DISCLAIMER_SHOW.get().booleanValue()).build();
        addRenderableWidget(this.checkbox);
        addRenderableWidget(new Button.Builder(Component.translatable("gui.sparkle_morpher.disclaimer.close"), button -> {
            if (this.checkbox.selected()) {
                GeneralConfig.DISCLAIMER_SHOW.set(false);
                Minecraft.getInstance().setScreen(new ModernPlayerModelScreen());
            } else {
                Minecraft.getInstance().setScreen(null);
            }
        }).size(btnW, 20).pos((this.width - btnW) / 2, (this.textHeight + i) - 20).build());
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(guiGraphics);
        guiGraphics.drawWordWrap(this.font, Component.translatable("gui.sparkle_morpher.disclaimer.text"), this.textY, this.textHeight, this.textWidth, -1);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}