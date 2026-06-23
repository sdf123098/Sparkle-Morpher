package com.micaftic.morpher.client.gui.button;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.entity.PlayerPreviewEntity;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.gui.ModelMetadataPresenter;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.RendererManager;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SRequestSwitchModelPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class TextureButton extends Button {

    public final PlayerPreviewEntity previewEntity;

    public final ModelAssembly modelAssembly;

    public TextureButton(int x, int y, PlayerPreviewEntity previewEntity, ModelAssembly modelAssembly) {
        super(x, y, 54, 102, Component.empty(), button -> {
        }, DEFAULT_NARRATION);
        this.previewEntity = previewEntity;
        this.modelAssembly = modelAssembly;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor guiGraphics = extractor;
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        guiGraphics.fillGradient(getX(), getY(), getX() + this.width, getY() + this.height, -12369342, -12369342);
        renderPlayerPreview(guiGraphics, partialTick);
        String str = this.previewEntity.getCurrentTextureName();
        MutableComponent mutableComponentLiteral = Component.literal(ModelMetadataPresenter.getLocalizedModelString(this.modelAssembly, "files.player.texture.%s".formatted(str), str));
        List listSplit = font.split(mutableComponentLiteral, 50);
        if (listSplit.size() > 1) {
            drawCenteredString(guiGraphics, font, (FormattedCharSequence) listSplit.get(0), getX() + (this.width / 2), (getY() + this.height) - 19, 0xFFF3F3E0);
            drawCenteredString(guiGraphics, font, (FormattedCharSequence) listSplit.get(1), getX() + (this.width / 2), (getY() + this.height) - 10, 0xFFF3F3E0);
        } else {
            drawCenteredString(guiGraphics, font, mutableComponentLiteral, getX() + (this.width / 2), (getY() + this.height) - 15, 0xFFF3F3E0);
        }
        if (isHoveredOrFocused()) {
            guiGraphics.fillGradient(getX(), getY() + 1, getX() + 1, (getY() + this.height) - 1, -790560, -790560);
            guiGraphics.fillGradient(getX(), getY(), getX() + this.width, getY() + 1, -790560, -790560);
            guiGraphics.fillGradient((getX() + this.width) - 1, getY() + 1, getX() + this.width, (getY() + this.height) - 1, -790560, -790560);
            guiGraphics.fillGradient(getX(), (getY() + this.height) - 1, getX() + this.width, getY() + this.height, -790560, -790560);
        }
    }

    @Override
    public void onPress(InputWithModifiers modifiers) {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            PlayerCapability.get(localPlayer).ifPresent(cap -> {
                cap.setCurrentTexture(this.previewEntity.getCurrentTextureName());
                ClientModelManager.rememberSelectedModel(this.previewEntity.getModelId(), this.previewEntity.getCurrentTextureName());
                if (!ClientModelManager.isLocalOnlyModel(this.previewEntity.getModelId())) {
                    NetworkHandler.sendToServer(new C2SRequestSwitchModelPacket(this.previewEntity.getModelId(), this.previewEntity.getCurrentTextureName()));
                }
            });
        }
    }

    public void renderPlayerPreview(GuiGraphicsExtractor guiGraphics, float partialTick) {
        int previewBottom = getY() + this.height - 20;
        guiGraphics.enableScissor(getX(), getY(), getX() + this.width, previewBottom);
        ModelPreviewRenderer.renderLivingEntityPreview(guiGraphics, getX(), getY(), getX() + this.width, previewBottom, getX() + (this.width / 2.0f), getY() + (this.height / 2.0f) + 24.0f, 35.0f, partialTick, this.previewEntity, RendererManager.getPlayerRenderer(), false, true);
        guiGraphics.disableScissor();
    }

    private static void drawCenteredString(GuiGraphicsExtractor guiGraphics, Font font, Component component, int centerX, int y, int color) {
        guiGraphics.text(font, component, centerX - (font.width(component) / 2), y, color, true);
    }

    private static void drawCenteredString(GuiGraphicsExtractor guiGraphics, Font font, FormattedCharSequence formattedCharSequence, int centerX, int y, int color) {
        guiGraphics.text(font, formattedCharSequence, centerX - (font.width(formattedCharSequence) / 2), y, color, true);
    }
}
