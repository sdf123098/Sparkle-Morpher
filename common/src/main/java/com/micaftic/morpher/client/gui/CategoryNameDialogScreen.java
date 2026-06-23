package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.gui.button.FlatColorButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class CategoryNameDialogScreen extends Screen {
    private final Screen parent;
    private final Component prompt;
    private final Consumer<String> onConfirm;
    private final String initialValue;
    private EditBox nameBox;
    private int guiLeft;
    private int guiTop;

    public CategoryNameDialogScreen(Screen parent, Component prompt, String initialValue, Consumer<String> onConfirm) {
        super(prompt);
        this.parent = parent;
        this.prompt = prompt;
        this.initialValue = initialValue == null ? "" : initialValue;
        this.onConfirm = onConfirm;
    }

    @Override
    public void init() {
        clearWidgets();
        this.guiLeft = (this.width - 260) / 2;
        this.guiTop = (this.height - 92) / 2;
        this.nameBox = new EditBox(this.font, this.guiLeft + 20, this.guiTop + 32, 220, 18, this.prompt);
        this.nameBox.setValue(this.initialValue);
        this.nameBox.setFocused(true);
        this.nameBox.moveCursorToEnd(false);
        addWidget(this.nameBox);
        addRenderableWidget(new FlatColorButton(this.guiLeft + 52, this.guiTop + 62, 64, 18, Component.translatable("gui.sparkle_morpher.model_select.confirm"), button -> confirm()));
        addRenderableWidget(new FlatColorButton(this.guiLeft + 144, this.guiTop + 62, 64, 18, Component.translatable("gui.sparkle_morpher.config.cancel"), button -> Minecraft.getInstance().setScreen(this.parent)));
    }

    private void confirm() {
        this.onConfirm.accept(this.nameBox.getValue());
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(extractor);
        extractor.fillGradient(this.guiLeft, this.guiTop, this.guiLeft + 260, this.guiTop + 92, -14540254, -14540254);
        extractor.text(this.font, this.prompt, this.guiLeft + 20, this.guiTop + 14, 0xFFF3F3E0);
        this.nameBox.extractWidgetRenderState(extractor, mouseX, mouseY, partialTick);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        return this.nameBox.charTyped(event) || super.charTyped(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == 257 || event.key() == 335) {
            confirm();
            return true;
        }
        return this.nameBox.keyPressed(event) || super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean flag) {
        if (this.nameBox.mouseClicked(event, flag)) {
            setFocused(this.nameBox);
            return true;
        }
        return super.mouseClicked(event, flag);
    }
}
