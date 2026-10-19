package com.micaftic.morpher.client.gui.button;

import com.micaftic.morpher.YesSteveModel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class IconButton extends FlatColorButton {

    private static final ResourceLocation ICON_TEXTURE = com.micaftic.morpher.core.api.resource.ResourceApi.nativeId(YesSteveModel.MOD_ID, "texture/ui/lucide.png");

    private final int iconU;

    private final int iconV;

    public IconButton(int x, int y, int width, int height, int iconU, int iconV, OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress);
        this.iconU = iconU;
        this.iconV = iconV;
    }

    @Override
    protected void renderWidget(GuiGraphics extractor, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(extractor, mouseX, mouseY, partialTick);
        GuiGraphics guiGraphics = extractor;
        int iconOffsetX = (this.width - 16) / 2;
        int iconOffsetY = (this.height - 16) / 2;
        int x = getX() + iconOffsetX;
        int y = getY() + iconOffsetY;
        com.micaftic.morpher.client.gui.LegacyGuiBlit.blit(guiGraphics,ICON_TEXTURE,x,y,x + 16,y + 16,this.iconU / 128.0f,(this.iconU + 16) / 128.0f,this.iconV / 128.0f,(this.iconV + 16) / 128.0f);
    }
}
