package com.micaftic.morpher.client.gui.button;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.capability.StarModelsCapability;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SSetStarModelPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ModIconButton extends FlatColorButton {

    private static final Identifier ICON_TEXTURE = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/icon.png");

    public ModIconButton(int x, int y) {
        super(x, y, 20, 20, Component.empty(), button -> {
        });
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractContents(extractor, mouseX, mouseY, partialTick);
        GuiGraphicsExtractor guiGraphics = extractor;
        int iconOffsetX = (this.width - 16) / 2;
        int iconOffsetY = (this.height - 16) / 2;
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            PlayerCapability.get(localPlayer).ifPresent(cap -> {
                StarModelsCapability.get(localPlayer).ifPresent(cap2 -> {
                    if (cap2.containsModel(cap.getModelId())) {
                        int x = getX() + iconOffsetX;
                        int y = getY() + iconOffsetY;
                        guiGraphics.blit(ICON_TEXTURE, x, y, x + 16, y + 16, 16.0f / 256.0f, 32.0f / 256.0f, 0.0f, 16.0f / 256.0f);
                    } else {
                        int x = getX() + iconOffsetX;
                        int y = getY() + iconOffsetY;
                        guiGraphics.blit(ICON_TEXTURE, x, y, x + 16, y + 16, 0.0f, 16.0f / 256.0f, 0.0f, 16.0f / 256.0f);
                    }
                });
            });
        }
    }

    @Override
    public void onPress(InputWithModifiers modifiers) {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            PlayerCapability.get(localPlayer).ifPresent(cap -> {
                StarModelsCapability.get(localPlayer).ifPresent(cap2 -> {
                    String str = cap.getModelId();
                    if (cap2.containsModel(str)) {
                        cap2.removeModel(str);
                        NetworkHandler.sendToServer(C2SSetStarModelPacket.remove(str));
                    } else {
                        cap2.addModel(str);
                        NetworkHandler.sendToServer(C2SSetStarModelPacket.add(str));
                    }
                });
            });
        }
    }
}
