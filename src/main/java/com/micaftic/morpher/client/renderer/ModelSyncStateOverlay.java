package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.config.LoadingStateConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import com.micaftic.morpher.core.api.client.HudOverlay;

public class ModelSyncStateOverlay implements HudOverlay {
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, Font font, float partialTick, int screenWidth, int screenHeight) {
        int textX;
        int textY;
        int barX;
        int barY;

        if (LoadingStateConfig.DISABLE_LOADING_STATE_SCREEN.get().booleanValue()) {
            return;
        }

        switch (LoadingStateConfig.LOADING_STATE_POSITION.get()) {
            case TOP_LEFT:
                textX = 10;
                textY = 10;
                barX = 10;
                barY = 22;
                break;
            case TOP_CENTER:
                textX = screenWidth / 2;
                textY = 10;
                barX = (screenWidth - 150) / 2;
                barY = 22;
                break;
            case TOP_RIGHT:
                textX = screenWidth - 10;
                textY = 10;
                barX = (screenWidth - 10) - 150;
                barY = 22;
                break;
            case BOTTOM_LEFT:
                textX = 10;
                textY = screenHeight - 30;
                barX = 10;
                barY = (screenHeight - 8) - 10;
                break;
            case BOTTOM_CENTER:
                textX = screenWidth / 2;
                textY = screenHeight - 85;
                barX = (screenWidth - 150) / 2;
                barY = (screenHeight - 63) - 10;
                break;
            case BOTTOM_RIGHT:
                textX = screenWidth - 10;
                textY = screenHeight - 30;
                barX = (screenWidth - 10) - 150;
                barY = (screenHeight - 8) - 10;
                break;
            default:
                textX = screenWidth / 2;
                textY = 10;
                barX = (screenWidth - 150) / 2;
                barY = 22;
                break;
        }

        ClientModelManager.SyncStatus syncStatus = ClientModelManager.getSyncStatus();

        if (syncStatus.getCurrentState() == ClientModelManager.SyncState.IDLE) {
            int pendingModelCount = ClientModelManager.getPendingModelCount();
            if (pendingModelCount > 0) {
                int loadedModelCount = ClientModelManager.getModelAssemblyMap().size();
                int totalModelCount = loadedModelCount + pendingModelCount;
                MutableComponent loadingText = Component.translatable("gui.sparkle_morpher.sync_hint.title").append(Component.translatable("gui.sparkle_morpher.sync_hint.loading_models", pendingModelCount, totalModelCount).withStyle(ChatFormatting.YELLOW));
                renderSyncText(font, guiGraphics, loadingText, textX, textY, screenWidth);
                guiGraphics.fill(barX, barY, barX + 150, barY + 10, -11184811);
                int progressWidth = (int) (150.0f * ((float) loadedModelCount / totalModelCount));
                guiGraphics.fill(barX, barY, barX + progressWidth, barY + 10, -256);
            }
            return;
        }

        MutableComponent prefixText = Component.translatable("gui.sparkle_morpher.sync_hint.title");

        switch (syncStatus.getCurrentState()) {
            case WAITING:
                prefixText.append(Component.translatable("gui.sparkle_morpher.sync_hint.waiting").withStyle(ChatFormatting.AQUA));
                break;
            case LOADING:
                prefixText.append(Component.translatable("gui.sparkle_morpher.sync_hint.loading").withStyle(ChatFormatting.GOLD));
                break;
            case PREPARING:
                prefixText.append(Component.translatable("gui.sparkle_morpher.sync_hint.preparing").withStyle(ChatFormatting.LIGHT_PURPLE));
                break;
            case SYNCING:
                if (syncStatus.getSyncedModels() == 0) {
                    prefixText.append(Component.translatable("gui.sparkle_morpher.sync_hint.syncing").withStyle(ChatFormatting.RED));
                } else {
                    prefixText.append(Component.literal(String.format("%s/%s", syncStatus.getSyncedModels(), syncStatus.getTotalModels())).withStyle(ChatFormatting.GREEN));
                    guiGraphics.fill(barX, barY, barX + 150, barY + 10, -11184811);
                    int progressWidth = (int) (150.0f * ((float) syncStatus.getSyncedModels() / syncStatus.getTotalModels()));
                    guiGraphics.fill(barX, barY, barX + progressWidth, barY + 10, -16711936);
                }
                break;
        }
        renderSyncText(font, guiGraphics, prefixText, textX, textY, screenWidth);
    }

    private void renderSyncText(Font font, GuiGraphicsExtractor guiGraphics, MutableComponent textComponent, int baseX, int textY, int screenWidth) {
        int drawX;
        int textWidth = font.width(textComponent);

        drawX = switch (LoadingStateConfig.LOADING_STATE_POSITION.get()) {
            case TOP_LEFT, BOTTOM_LEFT -> baseX;
            case TOP_CENTER, BOTTOM_CENTER -> (screenWidth - textWidth) / 2;
            case TOP_RIGHT, BOTTOM_RIGHT -> baseX - textWidth;
        };
        guiGraphics.text(font, textComponent, drawX, textY, TEXT_COLOR);
    }
}
