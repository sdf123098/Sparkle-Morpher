package com.micaftic.morpher.client.gui.button;

import com.micaftic.morpher.YesSteveModel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class IconButton extends FlatColorButton {

    private static final Identifier ICON_TEXTURE = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/model_panel_icons.png");

    private final int iconU;

    private final int iconV;

    public IconButton(int x, int y, int width, int height, int iconU, int iconV, OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress);
        this.iconU = iconU;
        this.iconV = iconV;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractContents(extractor, mouseX, mouseY, partialTick);
        GuiGraphicsExtractor guiGraphics = extractor;
        int iconOffsetX = (this.width - 16) / 2;
        int iconOffsetY = (this.height - 16) / 2;
        int x = getX() + iconOffsetX;
        int y = getY() + iconOffsetY;
        guiGraphics.blit(ICON_TEXTURE, x, y, x + 16, y + 16, this.iconU / 128.0f, (this.iconU + 16) / 128.0f, this.iconV / 64.0f, (this.iconV + 16) / 64.0f);
    }
}
