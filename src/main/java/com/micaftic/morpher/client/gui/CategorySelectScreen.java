package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.gui.button.FlatColorButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CategorySelectScreen extends Screen {
    private final Screen parent;
    private final Component titleText;
    private final List<String> categories;
    private final Consumer<String> onSelect;
    private final Runnable onCreate;
    private int guiLeft;
    private int guiTop;
    private int page;

    public CategorySelectScreen(Screen parent, Component titleText, List<String> categories, Consumer<String> onSelect, Runnable onCreate) {
        super(titleText);
        this.parent = parent;
        this.titleText = titleText;
        this.categories = new ArrayList<>(categories);
        this.onSelect = onSelect;
        this.onCreate = onCreate;
    }

    @Override
    protected void init() {
        clearWidgets();
        this.guiLeft = (this.width - 280) / 2;
        this.guiTop = (this.height - 210) / 2;
        int start = this.page * 8;
        for (int i = 0; i < 8; i++) {
            int index = start + i;
            if (index >= this.categories.size()) {
                break;
            }
            String category = this.categories.get(index);
            addRenderableWidget(new FlatColorButton(this.guiLeft + 20, this.guiTop + 34 + i * 20, 240, 16, Component.literal(category), button -> {
                Minecraft.getInstance().setScreen(this.parent);
                this.onSelect.accept(category);
            }));
        }
        addRenderableWidget(new FlatColorButton(this.guiLeft + 20, this.guiTop + 178, 52, 18, Component.translatable("gui.sparkle_morpher.pre_page"), button -> {
            if (this.page > 0) {
                this.page--;
                init();
            }
        }));
        addRenderableWidget(new FlatColorButton(this.guiLeft + 84, this.guiTop + 178, 52, 18, Component.translatable("gui.sparkle_morpher.next_page"), button -> {
            int maxPage = Math.max(0, (this.categories.size() - 1) / 8);
            if (this.page < maxPage) {
                this.page++;
                init();
            }
        }));
        if (this.onCreate != null) {
            addRenderableWidget(new FlatColorButton(this.guiLeft + 148, this.guiTop + 178, 52, 18, Component.translatable("gui.sparkle_morpher.model_select.new_category"), button -> this.onCreate.run()));
        }
        addRenderableWidget(new FlatColorButton(this.guiLeft + 212, this.guiTop + 178, 48, 18, Component.translatable("gui.sparkle_morpher.config.cancel"), button -> Minecraft.getInstance().setScreen(this.parent)));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(guiGraphics);
        guiGraphics.fill(this.guiLeft, this.guiTop, this.guiLeft + 280, this.guiTop + 210, 0xE0222222);
        guiGraphics.fill(this.guiLeft, this.guiTop, this.guiLeft + 280, this.guiTop + 2, 0xFFB15D2B);
        guiGraphics.drawString(this.font, this.titleText, this.guiLeft + 20, this.guiTop + 14, 0xFFF3F3E0, false);
        if (this.categories.isEmpty()) {
            guiGraphics.drawString(this.font, Component.translatable("gui.sparkle_morpher.model_select.no_category"), this.guiLeft + 20, this.guiTop + 76, 0xFFAAAAAA, false);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
