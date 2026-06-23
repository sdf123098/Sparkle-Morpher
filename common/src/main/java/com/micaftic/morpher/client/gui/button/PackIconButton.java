package com.micaftic.morpher.client.gui.button;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.resource.models.ModelPackData;
import com.micaftic.morpher.client.gui.ModelMetadataPresenter;
import com.micaftic.morpher.util.FileTypeUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;

public class PackIconButton extends Button {

    private static final ResourceLocation default_pack_icon = ResourceLocation.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/default_pack_icon.png");

    private final ModelPackData packData;

    public PackIconButton(int x, int y, int width, int height, ModelPackData packData, OnPress onPress) {
        super(x, y, width, height, Component.literal(ModelMetadataPresenter.getLocalizedString(packData, "name", packData.getName())), onPress, DEFAULT_NARRATION);
        this.packData = packData;
    }

    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        guiGraphics.fillGradient(getX(), getY(), getX() + this.width, getY() + this.height, -6598176, -6598176);
        ResourceLocation location = FileTypeUtil.getPackIconLocation(this.packData.getPath());
        AbstractTexture texture = minecraft.getTextureManager().getTexture(location, MissingTextureAtlasSprite.getTexture());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (texture == MissingTextureAtlasSprite.getTexture()) {
            guiGraphics.blit(default_pack_icon, getX(), getY(), 0.0f, 0.0f, this.width, this.height, this.width, this.height);
        } else {
            guiGraphics.blit(location, getX(), getY(), 0.0f, 0.0f, this.width, this.height, this.width, this.height);
        }
        RenderSystem.disableBlend();
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
                drawCenteredString(guiGraphics, font, (FormattedCharSequence) truncatedSplit.get(0), getX() + (this.width / 2), (getY() + this.height) - 19, 5592405);
                drawCenteredString(guiGraphics, font, (FormattedCharSequence) truncatedSplit.get(1), getX() + (this.width / 2), (getY() + this.height) - 10, 5592405);
            } else {
                drawCenteredString(guiGraphics, font, truncatedMsg, getX() + (this.width / 2), (getY() + this.height) - 15, 5592405);
            }
        } else if (listSplit.size() > 1) {
            drawCenteredString(guiGraphics, font, (FormattedCharSequence) listSplit.get(0), getX() + (this.width / 2), (getY() + this.height) - 19, 5592405);
            drawCenteredString(guiGraphics, font, (FormattedCharSequence) listSplit.get(1), getX() + (this.width / 2), (getY() + this.height) - 10, 5592405);
        } else {
            drawCenteredString(guiGraphics, font, getMessage(), getX() + (this.width / 2), (getY() + this.height) - 15, 5592405);
        }
        if (isHoveredOrFocused()) {
            guiGraphics.fillGradient(getX(), getY() + 1, getX() + 1, (getY() + this.height) - 1, -1982745, -1982745);
            guiGraphics.fillGradient(getX(), getY(), getX() + this.width, getY() + 1, -1982745, -1982745);
            guiGraphics.fillGradient((getX() + this.width) - 1, getY() + 1, getX() + this.width, (getY() + this.height) - 1, -1982745, -1982745);
            guiGraphics.fillGradient(getX(), (getY() + this.height) - 1, getX() + this.width, getY() + this.height, -1982745, -1982745);
        }
    }

    public void renderDescription(GuiGraphics guiGraphics, Screen screen, int mouseX, int mouseY) {
        String str = ModelMetadataPresenter.getLocalizedString(this.packData, "description", this.packData.getDescription());
        if (StringUtils.isBlank(str)) {
            return;
        }
        List<Component> listSingletonList = Collections.singletonList(Component.literal(str));
        if (isHovered()) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0f, 0.0f, 4000.0f);
            guiGraphics.renderComponentTooltip(Minecraft.getInstance().font, listSingletonList, mouseX, mouseY);
            guiGraphics.pose().popPose();
        }
    }

    private static void drawCenteredString(GuiGraphics guiGraphics, Font font, Component component, int centerX, int y, int color) {
        guiGraphics.drawString(font, component, centerX - (font.width(component) / 2), y, color, false);
    }

    private static void drawCenteredString(GuiGraphics guiGraphics, Font font, FormattedCharSequence formattedCharSequence, int centerX, int y, int color) {
        guiGraphics.drawString(font, formattedCharSequence, centerX - (font.width(formattedCharSequence) / 2), y, color, false);
    }
}