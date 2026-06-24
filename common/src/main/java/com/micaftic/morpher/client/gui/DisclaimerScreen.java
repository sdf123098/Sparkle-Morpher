package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.config.GeneralConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

    public DisclaimerScreen() {
        super(Component.literal("Disclaimer GUI"));
    }

    public void init() {
        clearWidgets();
        int size = this.font.split(Component.translatable("gui.sparkle_morpher.disclaimer.text"), 400).size();
        Objects.requireNonNull(this.font);
        int i = (size * 9) + 20 + 20 + 10 + 20;
        this.textY = (this.width - 400) / 2;
        this.textHeight = (this.height - i) / 2;
        MutableComponent mutableComponentTranslatable = Component.translatable("gui.sparkle_morpher.disclaimer.read");
        int iWidth = this.font.width(mutableComponentTranslatable);
        this.checkbox = Checkbox.builder(mutableComponentTranslatable, font).pos((this.width - iWidth) / 2, (this.textHeight + i) - 50).maxWidth(iWidth).selected(!GeneralConfig.DISCLAIMER_SHOW.get().booleanValue()).build();
        addRenderableWidget(this.checkbox);
        addRenderableWidget(new Button.Builder(Component.translatable("gui.sparkle_morpher.disclaimer.close"), button -> {
            if (this.checkbox.selected()) {
                GeneralConfig.DISCLAIMER_SHOW.set(false);
                GeneralConfig.DISCLAIMER_SHOW.save();
                Minecraft.getInstance().setScreen(new ModernPlayerModelScreen());
            } else {
                Minecraft.getInstance().setScreen(null);
            }
        }).size(300, 20).pos((this.width - 300) / 2, (this.textHeight + i) - 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor guiGraphics = extractor;
        extractTransparentBackground(extractor);
/*         GuiGraphicsExtractor.drawWordWrap(this.font, Component.translatable("gui.sparkle_morpher.disclaimer.text"), this.textY, this.textHeight, 400, -1); */
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }
}
