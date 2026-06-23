package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.model.ServerModelManager;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;


public class OpenModelFolderScreen extends Screen {

    private final PlayerModelScreen parentScreen;

    public OpenModelFolderScreen(PlayerModelScreen modelScreen) {
        super(Component.literal("Open Model Folder"));
        this.parentScreen = modelScreen;
    }

    public void init() {
        int x = (this.width - 310) / 2;
        int y = (this.height / 2) + 60;
        clearWidgets();
        addRenderableWidget(Button.builder(Component.translatable("gui.sparkle_morpher.open_model_folder.open"), button -> {
            Util.getPlatform().openFile(ServerModelManager.CUSTOM.toFile());
        }).bounds(x, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.sparkle_morpher.model.return"), button2 -> {
            Minecraft.getInstance().setScreen(this.parentScreen);
        }).bounds(x + 160, y, 150, 20).build());
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(guiGraphics);
        guiGraphics.drawWordWrap(this.font, Component.translatable("gui.sparkle_morpher.open_model_folder.tips"), (this.width - 400) / 2, (this.height / 2) - 80, 400, 16777215);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
