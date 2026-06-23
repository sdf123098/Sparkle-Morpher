package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.config.LoadingStateConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import com.micaftic.morpher.core.api.client.HudOverlay;

public class ModelSyncStateOverlay implements HudOverlay {

    private static final int BAR_WIDTH = 150;
    private static final int BAR_HEIGHT = 10;
    private static final int BAR_BG = 0xFF555555;

    private static float animatedProgress = 0.0f;
    private static long lastFrameNanos = 0L;
    private static float shimmerPhase = 0.0f;

    @Override
    public void render(GuiGraphics guiGraphics, Font font, float partialTick, int screenWidth, int screenHeight) {
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
                drawAnimatedBar(guiGraphics, barX, barY, (float) loadedModelCount / totalModelCount, 0xFFFFD11A, true);
            } else {
                resetAnimation();
            }
            return;
        }

        MutableComponent prefixText = Component.translatable("gui.sparkle_morpher.sync_hint.title");

        switch (syncStatus.getCurrentState()) {
            case WAITING:
                prefixText.append(Component.translatable("gui.sparkle_morpher.sync_hint.waiting").withStyle(ChatFormatting.AQUA));
                resetAnimation();
                break;
            case LOADING:
                prefixText.append(Component.translatable("gui.sparkle_morpher.sync_hint.loading").withStyle(ChatFormatting.GOLD));
                resetAnimation();
                break;
            case PREPARING:
                prefixText.append(Component.translatable("gui.sparkle_morpher.sync_hint.preparing").withStyle(ChatFormatting.LIGHT_PURPLE));
                resetAnimation();
                break;
            case SYNCING:
                if (syncStatus.getSyncedModels() == 0) {
                    prefixText.append(Component.translatable("gui.sparkle_morpher.sync_hint.syncing").withStyle(ChatFormatting.RED));
                    resetAnimation();
                } else {
                    prefixText.append(Component.literal(String.format("%s/%s", syncStatus.getSyncedModels(), syncStatus.getTotalModels())).withStyle(ChatFormatting.GREEN));
                    drawAnimatedBar(guiGraphics, barX, barY, (float) syncStatus.getSyncedModels() / syncStatus.getTotalModels(), 0xFF55FF55, true);
                }
                break;
        }
        renderSyncText(font, guiGraphics, prefixText, textX, textY, screenWidth);
    }

    private static void drawAnimatedBar(GuiGraphics g, int x, int y, float target, int fgColor, boolean shimmer) {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        float dt = Math.min(0.1f, (now - lastFrameNanos) / 1.0e9f);
        lastFrameNanos = now;
        float lerp = 1.0f - (float) Math.exp(-dt * 12.0f);
        target = Math.max(0.0f, Math.min(1.0f, target));
        animatedProgress += (target - animatedProgress) * lerp;
        if (Math.abs(target - animatedProgress) < 0.001f) animatedProgress = target;
        shimmerPhase = (shimmerPhase + dt * 1.2f) % 1.0f;

        g.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, BAR_BG);
        int fillW = Math.round(animatedProgress * BAR_WIDTH);
        if (fillW > 0) {
            g.fill(x, y, x + fillW, y + BAR_HEIGHT, fgColor);
            int highlight = (fgColor & 0x00FFFFFF) | 0x60000000;
            g.fill(x, y, x + fillW, y + 1, lighten(fgColor));
            if (shimmer && fillW > 8) {
                int shimmerX = x + Math.round(shimmerPhase * fillW);
                int shimmerW = Math.min(12, fillW - (shimmerX - x));
                if (shimmerW > 0) {
                    g.fill(shimmerX, y, shimmerX + shimmerW, y + BAR_HEIGHT, highlight);
                }
            }
        }
    }

    private static int lighten(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 60);
        int gn = Math.min(255, ((argb >> 8) & 0xFF) + 60);
        int b = Math.min(255, (argb & 0xFF) + 60);
        return (a << 24) | (r << 16) | (gn << 8) | b;
    }

    private static void resetAnimation() {
        animatedProgress = 0.0f;
        lastFrameNanos = 0L;
        shimmerPhase = 0.0f;
    }

    private void renderSyncText(Font font, GuiGraphics guiGraphics, MutableComponent textComponent, int baseX, int textY, int screenWidth) {
        int drawX;
        int textWidth = font.width(textComponent);

        drawX = switch (LoadingStateConfig.LOADING_STATE_POSITION.get()) {
            case TOP_LEFT, BOTTOM_LEFT -> baseX;
            case TOP_CENTER, BOTTOM_CENTER -> (screenWidth - textWidth) / 2;
            case TOP_RIGHT, BOTTOM_RIGHT -> baseX - textWidth;
        };
        guiGraphics.drawString(font, textComponent, drawX, textY, 16777215);
    }
}
