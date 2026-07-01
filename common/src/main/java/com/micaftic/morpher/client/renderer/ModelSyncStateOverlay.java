package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.config.LoadingStateConfig;
import com.micaftic.morpher.core.api.client.HudOverlay;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ModelSyncStateOverlay implements HudOverlay {
    private static final ResourceLocation MODEL_PANEL_ICONS = ResourceLocation.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/model_panel_icons.png");
    private static final int WIDTH = 190;
    private static final int HEIGHT = 42;
    private static final int GLASS = 0xD21F282E;
    private static final int PANEL = 0xA0405058;
    private static final int BORDER = 0xB8E4F5FF;
    private static final int TRACK = 0x66303030;
    private static final int RED = 0xFFE05252;
    private static final int GOLD = 0xFFFFC857;
    private static final int GREEN = 0xFF4CAF50;
    private static final int BLUE = 0xFF5ECAE8;
    private static final int TEXT = 0xFFEDE1CC;
    private static final int MUTED = 0xFF9A9A9A;

    @Override
    public void render(GuiGraphics g, Font font, float partialTick, int screenWidth, int screenHeight) {
        if (LoadingStateConfig.DISABLE_LOADING_STATE_SCREEN.get().booleanValue()) {
            return;
        }
        PopupState state = resolveState();
        if (state == null) {
            return;
        }
        int x = anchorX(screenWidth) + safeInt(LoadingStateConfig.LOADING_STATE_OFFSET_X.get(), 0);
        int y = anchorY(screenHeight) + safeInt(LoadingStateConfig.LOADING_STATE_OFFSET_Y.get(), 0);
        x = Math.max(4, Math.min(screenWidth - WIDTH - 4, x));
        y = Math.max(4, Math.min(screenHeight - HEIGHT - 4, y));

        fill(g, x, y, WIDTH, HEIGHT, GLASS);
        fill(g, x + 1, y + 1, WIDTH - 2, HEIGHT - 2, PANEL);
        border(g, x, y, WIDTH, HEIGHT, BORDER);
        drawStatusIcon(g, x + 11, y + 12, state.color(), state.kind());
        g.drawString(font, state.title(), x + 32, y + 8, TEXT, false);
        Component detail = trim(font, state.detail(), WIDTH - 42);
        g.drawString(font, detail, x + 32, y + 21, MUTED, false);
        drawProgress(g, x + 32, y + 34, WIDTH - 44, 3, state.progress(), state.color());
    }

    private static PopupState resolveState() {
        ClientModelManager.SyncStatus syncStatus = ClientModelManager.getSyncStatus();
        int pending = ClientModelManager.getPendingModelCount();
        long terminalSince = syncStatus.getTerminalSinceMillis();
        if (terminalSince > 0L) {
            int seconds = safeInt(LoadingStateConfig.LOADING_STATE_AUTO_HIDE_SECONDS.get(), 4);
            if (System.currentTimeMillis() - terminalSince > seconds * 1000L) {
                return null;
            }
            if (syncStatus.getMessage() != null) {
                return new PopupState(Kind.FAILURE, Component.translatable("gui.sparkle_morpher.sync_hint.failed"), syncStatus.getMessage(), RED, 1.0f);
            }
            int total = Math.max(0, syncStatus.getTotalModels());
            return new PopupState(Kind.SUCCESS, Component.translatable("gui.sparkle_morpher.sync_hint.success"), Component.translatable("gui.sparkle_morpher.sync_hint.completed_models", total), GREEN, 1.0f);
        }
        if (pending > 0) {
            int loaded = ClientModelManager.getModelAssemblyMap().size();
            int total = Math.max(1, loaded + pending);
            return new PopupState(Kind.ACTIVE, Component.translatable("gui.sparkle_morpher.sync_hint.loading"), Component.translatable("gui.sparkle_morpher.sync_hint.loading_models_precise", loaded, total, pending), GOLD, (float) loaded / total);
        }
        return switch (syncStatus.getCurrentState()) {
            case WAITING -> new PopupState(Kind.ACTIVE, Component.translatable("gui.sparkle_morpher.sync_hint.waiting"), Component.translatable("gui.sparkle_morpher.sync_hint.waiting_detail"), BLUE, 0.0f);
            case LOADING -> new PopupState(Kind.ACTIVE, Component.translatable("gui.sparkle_morpher.sync_hint.loading"), Component.translatable("gui.sparkle_morpher.sync_hint.loading_detail"), GOLD, 0.18f);
            case PREPARING -> new PopupState(Kind.ACTIVE, Component.translatable("gui.sparkle_morpher.sync_hint.preparing"), Component.translatable("gui.sparkle_morpher.sync_hint.preparing_detail"), BLUE, 0.34f);
            case SYNCING -> {
                int total = Math.max(1, syncStatus.getTotalModels());
                int synced = Math.max(0, syncStatus.getSyncedModels());
                yield new PopupState(Kind.ACTIVE, Component.translatable("gui.sparkle_morpher.sync_hint.syncing"), Component.translatable("gui.sparkle_morpher.sync_hint.syncing_models", synced, total), GREEN, (float) synced / total);
            }
            case IDLE -> null;
        };
    }

    private static int anchorX(int screenWidth) {
        return switch (LoadingStateConfig.LOADING_STATE_POSITION.get()) {
            case TOP_LEFT, BOTTOM_LEFT -> 10;
            case TOP_CENTER, BOTTOM_CENTER -> (screenWidth - WIDTH) / 2;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - WIDTH - 10;
        };
    }

    private static int anchorY(int screenHeight) {
        return switch (LoadingStateConfig.LOADING_STATE_POSITION.get()) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> 10;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> screenHeight - HEIGHT - 10;
        };
    }

    private static Component trim(Font font, Component component, int maxWidth) {
        String text = component.getString();
        if (font.width(text) <= maxWidth) {
            return component;
        }
        String ellipsis = "...";
        int keep = text.length();
        while (keep > 0 && font.width(text.substring(0, keep) + ellipsis) > maxWidth) {
            keep--;
        }
        return Component.literal(text.substring(0, Math.max(0, keep)) + ellipsis);
    }

    private static void drawProgress(GuiGraphics g, int x, int y, int w, int h, float progress, int color) {
        fill(g, x, y, w, h, TRACK);
        int fillW = Math.max(0, Math.min(w, Math.round(w * progress)));
        if (fillW > 0) {
            fill(g, x, y, fillW, h, color);
        }
    }

    private static void drawStatusIcon(GuiGraphics g, int x, int y, int color, Kind kind) {
        fill(g, x, y, 14, 14, 0x55303030);
        border(g, x, y, 14, 14, color);
        int u = switch (kind) {
            case ACTIVE -> 80;
            case SUCCESS -> 112;
            case FAILURE -> 64;
        };
        int v = kind == Kind.ACTIVE ? 0 : kind == Kind.SUCCESS ? 48 : 32;
        g.blit(MODEL_PANEL_ICONS, x - 1, y - 1, 16, 16, u, v, 16, 16, 128, 64);
    }

    private static void fill(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        fill(g, x, y, w, 1, color);
        fill(g, x, y + h - 1, w, 1, color);
        fill(g, x, y, 1, h, color);
        fill(g, x + w - 1, y, 1, h, color);
    }

    private static int safeInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private record PopupState(Kind kind, Component title, Component detail, int color, float progress) {}

    private enum Kind {
        ACTIVE,
        SUCCESS,
        FAILURE
    }
}
