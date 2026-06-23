package com.micaftic.morpher.client.gui.button;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.resource.models.ModelPackData;
import com.micaftic.morpher.client.gui.ModelMetadataPresenter;
import com.micaftic.morpher.util.FileTypeUtil;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;

public class PackIconButton extends Button {

    private static final Identifier default_pack_icon = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/default_pack_icon.png");

    private final ModelPackData packData;

    public PackIconButton(int x, int y, int width, int height, ModelPackData packData, OnPress onPress) {
        super(x, y, width, height, Component.literal(ModelMetadataPresenter.getLocalizedString(packData, "name", packData.getName())), onPress, DEFAULT_NARRATION);
        this.packData = packData;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor guiGraphics = extractor;
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        guiGraphics.fillGradient(getX(), getY(), getX() + this.width, getY() + this.height, -6598176, -6598176);
        Identifier location = FileTypeUtil.getPackIconLocation(this.packData.getPath());
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 771, 1, 0);
        Identifier iconLocation = getReadyIconLocation(minecraft, location, this.packData.getTexture());
        guiGraphics.blit(iconLocation, getX(), getY(), getX() + this.width, getY() + this.height, 0.0f, 1.0f, 0.0f, 1.0f);
        GlStateManager._disableBlend();
        guiGraphics.fillGradient(getX(), getY() + this.height - 24, getX() + this.width, getY() + this.height, 0xAA000000, 0xAA000000);
        int maxTextWidth = this.width - 4;
        List listSplit = font.split(getMessage(), maxTextWidth);
        if (listSplit.size() > 2) {
            String plainText = getMessage().getString();
            String truncated = plainText;
            while (font.split(Component.literal(truncated + "..."), maxTextWidth).size() > 2 && truncated.length() > 1) {
                truncated = truncated.substring(0, truncated.length() - 1);
            }
            Component truncatedMsg = Component.literal(truncated + "...");
            List truncatedSplit = font.split(truncatedMsg, maxTextWidth);
            if (truncatedSplit.size() > 1) {
                drawCenteredString(guiGraphics, font, (FormattedCharSequence) truncatedSplit.get(0), getX() + (this.width / 2), (getY() + this.height) - 19, 0xFF557777);
                drawCenteredString(guiGraphics, font, (FormattedCharSequence) truncatedSplit.get(1), getX() + (this.width / 2), (getY() + this.height) - 10, 0xFF557777);
            } else {
                drawCenteredString(guiGraphics, font, truncatedMsg, getX() + (this.width / 2), (getY() + this.height) - 15, 0xFF557777);
            }
        } else if (listSplit.size() > 1) {
            drawCenteredString(guiGraphics, font, (FormattedCharSequence) listSplit.get(0), getX() + (this.width / 2), (getY() + this.height) - 19, 0xFF557777);
            drawCenteredString(guiGraphics, font, (FormattedCharSequence) listSplit.get(1), getX() + (this.width / 2), (getY() + this.height) - 10, 0xFF557777);
        } else {
            drawCenteredString(guiGraphics, font, getMessage(), getX() + (this.width / 2), (getY() + this.height) - 15, 0xFF557777);
        }
        if (isHoveredOrFocused()) {
            guiGraphics.fillGradient(getX(), getY() + 1, getX() + 1, (getY() + this.height) - 1, -1982745, -1982745);
            guiGraphics.fillGradient(getX(), getY(), getX() + this.width, getY() + 1, -1982745, -1982745);
            guiGraphics.fillGradient((getX() + this.width) - 1, getY() + 1, getX() + this.width, (getY() + this.height) - 1, -1982745, -1982745);
            guiGraphics.fillGradient(getX(), (getY() + this.height) - 1, getX() + this.width, getY() + this.height, -1982745, -1982745);
        }
    }

    public void renderDescription(GuiGraphicsExtractor guiGraphics, Screen screen, int mouseX, int mouseY) {
        String str = ModelMetadataPresenter.getLocalizedString(this.packData, "description", this.packData.getDescription());
        if (StringUtils.isBlank(str)) {
            return;
        }
        List<Component> listSingletonList = Collections.singletonList(Component.literal(str));
        if (isHovered()) {
            guiGraphics.setComponentTooltipForNextFrame(Minecraft.getInstance().font, listSingletonList, mouseX, mouseY);
        }
    }

    private static void drawCenteredString(GuiGraphicsExtractor guiGraphics, Font font, Component component, int centerX, int y, int color) {
        guiGraphics.text(font, component, centerX - (font.width(component) / 2), y, color, false);
    }

    private static void drawCenteredString(GuiGraphicsExtractor guiGraphics, Font font, FormattedCharSequence formattedCharSequence, int centerX, int y, int color) {
        guiGraphics.text(font, formattedCharSequence, centerX - (font.width(formattedCharSequence) / 2), y, color, false);
    }

    private static Identifier getReadyIconLocation(Minecraft minecraft, Identifier location, OuterFileTexture packIconTexture) {
        if (packIconTexture == null) {
            return default_pack_icon;
        }
        AbstractTexture texture = minecraft.getTextureManager().getTexture(location);
        if (!(texture instanceof OuterFileTexture outerFileTexture)) {
            return default_pack_icon;
        }
        if (!outerFileTexture.isLoaded()) {
            try {
                outerFileTexture.doLoad();
            } catch (RuntimeException ignored) {
                return default_pack_icon;
            }
        }
        return hasTextureView(texture) ? location : default_pack_icon;
    }

    private static boolean hasTextureView(AbstractTexture texture) {
        try {
            texture.getTextureView();
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
