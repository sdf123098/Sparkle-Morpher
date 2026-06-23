package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.gui.button.FlatColorButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class CategoryDeleteConfirmScreen extends Screen {
    private final Screen parent;
    private final String category;
    private final Consumer<Boolean> onConfirm;
    private int guiLeft;
    private int guiTop;

    public CategoryDeleteConfirmScreen(Screen parent, String category, Consumer<Boolean> onConfirm) {
        super(Component.translatable("gui.sparkle_morpher.model_select.delete_category_confirm", category));
        this.parent = parent;
        this.category = category;
        this.onConfirm = onConfirm;
    }

    @Override
    public void init() {
        clearWidgets();
        this.guiLeft = (this.width - 300) / 2;
        this.guiTop = (this.height - 100) / 2;
        addRenderableWidget(new FlatColorButton(this.guiLeft + 20, this.guiTop + 64, 78, 18, Component.translatable("gui.sparkle_morpher.model_select.delete_category_keep_models"), button -> confirm(false)));
        addRenderableWidget(new FlatColorButton(this.guiLeft + 111, this.guiTop + 64, 78, 18, Component.translatable("gui.sparkle_morpher.model_select.delete_category_with_models"), button -> confirm(true)));
        addRenderableWidget(new FlatColorButton(this.guiLeft + 202, this.guiTop + 64, 78, 18, Component.translatable("gui.sparkle_morpher.config.cancel"), button -> Minecraft.getInstance().setScreen(this.parent)));
    }

    private void confirm(boolean deleteModels) {
        this.onConfirm.accept(deleteModels);
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(extractor);
        extractor.fillGradient(this.guiLeft, this.guiTop, this.guiLeft + 300, this.guiTop + 100, -14540254, -14540254);
        extractor.text(this.font, Component.translatable("gui.sparkle_morpher.model_select.delete_category_confirm", this.category), this.guiLeft + 20, this.guiTop + 16, 0xFFF3F3E0);
        extractor.text(this.font, Component.translatable("gui.sparkle_morpher.model_select.delete_category_hint"), this.guiLeft + 20, this.guiTop + 34, 0xFFAAAAAA);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }
}
