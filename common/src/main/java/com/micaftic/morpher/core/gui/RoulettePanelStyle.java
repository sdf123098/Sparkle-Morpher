package com.micaftic.morpher.core.gui;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.core.gpu.BlurStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class RoulettePanelStyle {
    public static final Identifier MODEL_PANEL_ICONS = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/model_panel_icons.png");

    public static final int BG = 0x90171A1D;
    public static final int PANEL = 0x4A34424A;
    public static final int PANEL_HOVER = 0x66576B76;
    public static final int PANEL_ACTIVE = 0x625E7784;
    public static final int GLASS = 0x60405058;
    public static final int BORDER = 0x6EE4F5FF;
    public static final int RED = 0xFFE05252;
    public static final int RED_SOFT = 0x77E05252;
    public static final int TEXT = 0xFFEDE1CC;
    public static final int MUTED = 0xFF9A9A9A;
    public static final int ICON = 18;

    private RoulettePanelStyle() {
    }

    public enum Glyph {
        MODEL(0, 0),
        SETTINGS(32, 0),
        ROULETTE(80, 0),
        APPLY(0, 16),
        RELOAD(48, 16),
        UP(64, 16),
        ROOT(80, 16),
        CLEAR(48, 32),
        CANCEL(64, 32),
        CLOSE(80, 32),
        SAVE(96, 32),
        DELETE(112, 32),
        CREATE(0, 48),
        MOVE(16, 48),
        PLUS(32, 48),
        MINUS(48, 48),
        INFO(96, 48),
        CHECK(112, 48);

        public final int u;
        public final int v;

        Glyph(int u, int v) {
            this.u = u;
            this.v = v;
        }
    }

    public static void fill(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fillGradient(x, y, x + w, y + h, color, color);
    }

    public static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        fill(g, x, y, w, 1, color);
        fill(g, x, y + h - 1, w, 1, color);
        fill(g, x, y, 1, h, color);
        fill(g, x + w - 1, y, 1, h, color);
    }

    public static void glassPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        blurGlass(g, x, y, w, h, 0x34F1FBFF, 8.0f);
        fill(g, x, y, w, h, GLASS);
        border(g, x, y, w, h, BORDER);
    }

    public static void secondaryGlassPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        blurGlass(g, x, y, w, h, 0x58F7FBFF, 12.0f);
        fill(g, x, y, w, h, 0xD21F282E);
        border(g, x, y, w, h, 0xC8E4F5FF);
    }

    public static void blurGlass(GuiGraphicsExtractor g, int x, int y, int w, int h, int tint, float radius) {
        if (w <= 0 || h <= 0) {
            return;
        }
        BlurStack.pushBlur(x, y, w, h, 0.0f, radius, tint);
        BlurStack.flush(g);
    }

    public static void iconButton(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, Glyph glyph, boolean active) {
        boolean hover = active && inside(mouseX, mouseY, x, y, ICON, ICON);
        fill(g, x, y, ICON, ICON, !active ? 0x44282828 : hover ? PANEL_HOVER : 0x66303030);
        border(g, x, y, ICON, ICON, !active ? 0x22888888 : hover ? RED : 0x33FFFFFF);
        drawIcon(g, glyph, x + 1, y + 1);
    }

    public static void drawIcon(GuiGraphicsExtractor g, Glyph glyph, int x, int y) {
        g.blit(MODEL_PANEL_ICONS, x, y, x + 16, y + 16,
                glyph.u / 128.0f, (glyph.u + 16) / 128.0f,
                glyph.v / 64.0f, (glyph.v + 16) / 64.0f);
    }

    public static void drawCentered(GuiGraphicsExtractor g, Font font, Component text, int centerX, int y, int color) {
        g.text(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    public static String trim(Font font, String value, int maxWidth) {
        String text = value == null ? "" : value;
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int keep = text.length();
        while (keep > 0 && font.width(text.substring(0, keep) + ellipsis) > maxWidth) {
            keep--;
        }
        return text.substring(0, Math.max(0, keep)) + ellipsis;
    }

    public static boolean inside(double px, double py, int x, int y, int w, int h) {
        return px >= x && py >= y && px < x + w && py < y + h;
    }
}
