package com.micaftic.morpher.core.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Shared chrome for the handoff's independent editors, without the application rail. */
public record IndependentPanelChrome(int left, int top, int right, int bottom, int inset) {
    public static IndependentPanelChrome of(int width, int height) {
        int margin = width < 560 || height < 320 ? 12 : width < 800 ? 18 : 24;
        return new IndependentPanelChrome(margin, margin, width - margin, height - margin,
                margin == 24 ? 16 : margin == 18 ? 12 : 8);
    }

    public int contentLeft() { return left + inset; }
    public int contentRight() { return right - inset; }
    public int headerY() { return top + 6; }
    public int bodyTop() { return top + 62; }
    public int footerY() { return bottom - 30; }
    public int footerRight() { return contentRight(); }

    public void frame(GuiGraphicsExtractor g, Font font, Component title, Component subtitle,
                      int mouseX, int mouseY) {
        RoulettePanelStyle.glassPanel(g, left, top, right - left, bottom - top);
        icon(g, mouseX, mouseY, contentRight() - 22, headerY(), RoulettePanelStyle.Glyph.CLOSE);
        text(g, font, title, contentLeft(), UiElementLayout.centeredY(headerY(),22,Math.round(font.lineHeight*1.4f)), 1.4f, 0xFFF4F4F4);
        text(g, font, subtitle, contentLeft(), top + 29, .89f, 0xFFB1B1B1);
        RoulettePanelStyle.fill(g, contentLeft(), top + 49, contentRight() - contentLeft(), 1, 0x1AFFFFFF);
    }

    public boolean isBackOrClose(double x, double y) {
        return inside(x, y, contentRight() - 22, headerY(), 22, 22);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static void text(GuiGraphicsExtractor g, Font font, Component text, int x, int y, float scale, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        float fitted=(rightWidth()<560?Math.min(scale,1.1f):rightWidth()<800?Math.min(scale,1.25f):scale);
        g.pose().scale(fitted,fitted);
        g.text(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    public static void icon(GuiGraphicsExtractor g, int mx, int my, int x, int y, RoulettePanelStyle.Glyph glyph) {
        int size=buttonSize();
        boolean hover = inside(mx, my, x, y, size, size);
        RoulettePanelStyle.roundedSurface(g, x, y, size, size, 3, hover ? 0x18FFFFFF : 0x0CFFFFFF, 0x22FFFFFF);
        RoulettePanelStyle.drawIconScaled(g, glyph,x+(size-12)/2,y+(size-12)/2,12);
    }

    private static int rightWidth(){return net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();}
    private static int buttonSize(){return rightWidth()<560?18:rightWidth()<800?20:22;}

    public static void button(GuiGraphicsExtractor g, Font font, int mx, int my, int x, int y,
                              int w, Component label, boolean primary) {
        int size=buttonSize();
        boolean hover = inside(mx, my, x, y, w, size);
        RoulettePanelStyle.roundedSurface(g, x, y, w, size, 4,
                primary ? hover ? 0xFFEA6068 : 0xFFDC5159 : hover ? 0x18FFFFFF : 0x0EFFFFFF, 0x24FFFFFF);
        g.centeredText(font, label, x + w / 2, UiElementLayout.centeredY(y,size,font.lineHeight), 0xFFF4F4F4);
    }
}
