package com.micaftic.morpher.core.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import com.micaftic.morpher.core.gui.Option;
import com.micaftic.morpher.core.gui.OptionRow;

import java.awt.*;

public class EnumOptionRow<E extends Enum<E>> extends OptionRow<E> {
    private final E[] values;
    private boolean open;
    private float listScroll;

    public EnumOptionRow(int x, int y, int width, int height, Option<E> option, E[] values) {
        super(x, y, width, height, option);
        this.values = values;
    }

    @Override
    protected int controlWidth() {
        return Mth.clamp(width / 2, 100, 220);
    }

    @Override
    protected void renderControl(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = controlX();
        int cy = controlY();
        int cw = controlWidth();
        int ch = controlHeight();
        boolean hover = isMouseOverControl(mouseX, mouseY);

        g.fill(cx, cy, cx + cw, cy + ch, blendBg(hover, 0x3EC8C8C8));
        g.renderOutline(cx, cy, cw, ch, 0x60FFFFFF);

        Component text = Component.literal(prettify(option.get().name()));
        g.drawString(Minecraft.getInstance().font, text, cx + 6, cy + (ch - 8) / 2, 0xFFFFFFFF, false);

        int arrowX = cx + cw - 10;
        int arrowY = cy + ch / 2 - 1;
        g.fill(arrowX, arrowY, arrowX + 6, arrowY + 1, 0xFFCCCCCC);
        g.fill(arrowX + 1, arrowY + 1, arrowX + 5, arrowY + 2, 0xFFCCCCCC);
        g.fill(arrowX + 2, arrowY + 2, arrowX + 4, arrowY + 3, 0xFFCCCCCC);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (!isMouseOverControl(mouseX, mouseY)) return;
        open = !open;
        if (open) {
            int cur = currentIndex();
            int firstVisible = (int) (listScroll / 14);
            if (cur < firstVisible || cur >= firstVisible + 8) {
                listScroll = Math.max(0, Math.min(cur, values.length - 8)) * 14;
            }
        }
    }

    @Override
    public boolean isOverlayOpen() {
        return open;
    }

    @Override
    public void closeOverlay() {
        open = false;
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick, float scrollDisplay) {
        if (!open) return;
        int cx = controlX();
        int cw = controlWidth();
        int cy = controlY() - (int) scrollDisplay;
        int ch = controlHeight();
        int visible = Math.min(8, values.length);
        int listH = visible * 14 + 2;
        int listX = cx;
        int listY = cy + ch;
        g.pose().pushPose();
        g.pose().translate(0.0f, 0.0f, 200.0f);
        g.fill(listX, listY, listX + cw, listY + listH, 0xFF111111);
        int first = (int) (listScroll / 14);
        first = Math.max(0, Math.min(first, Math.max(0, values.length - visible)));
        for (int i = 0; i < visible; i++) {
            int idx = first + i;
            if (idx >= values.length) break;
            int itemY = listY + 1 + i * 14;
            boolean hover = mouseX >= listX && mouseX < listX + cw && mouseY >= itemY && mouseY < itemY + 14;
            boolean selected = idx == currentIndex();
            int bg = selected ? new Color(255,255,255,60).getRGB() : (hover ? 0xFF333333 : 0);
            if (bg != 0) g.fill(listX + 1, itemY, listX + cw - 1, itemY + 14, bg);
            g.drawString(Minecraft.getInstance().font, Component.literal(prettify(values[idx].name())), listX + 6, itemY + (14 - 8) / 2, -1, true);
        }
        if (values.length > visible) {
            int trackX = listX + cw - 3;
            int trackTop = listY + 1;
            int trackBot = listY + listH - 1;
            int trackH = trackBot - trackTop;
            int thumbH = Math.max(8, trackH * visible / values.length);
            int thumbY = trackTop + (int) ((trackH - thumbH) * listScroll / Math.max(1, (values.length - visible) * 14));
            g.fill(trackX, trackTop, trackX + 2, trackBot, 0x80444444);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFFAAAAAA);
        }
        g.pose().popPose();
    }

    @Override
    public boolean overlayMouseClicked(double mouseX, double mouseY, int button, float scrollDisplay) {
        if (!open) return false;
        int cx = controlX();
        int cw = controlWidth();
        int cy = controlY() - (int) scrollDisplay;
        int ch = controlHeight();
        int visible = Math.min(8, values.length);
        int listH = visible * 14 + 2;
        int listX = cx;
        int listY = cy + ch;
        if (mouseX < listX || mouseX >= listX + cw || mouseY < listY || mouseY >= listY + listH) return false;
        int first = (int) (listScroll / 14);
        first = Math.max(0, Math.min(first, Math.max(0, values.length - visible)));
        int slot = (int) ((mouseY - listY - 1) / 14);
        int idx = first + slot;
        if (idx >= 0 && idx < values.length) {
            option.setPending(values[idx]);
            open = false;
        }
        return true;
    }

    @Override
    public boolean overlayMouseScrolled(double mouseX, double mouseY, double delta, float scrollDisplay) {
        if (!open) return false;
        int cx = controlX();
        int cw = controlWidth();
        int cy = controlY() - (int) scrollDisplay;
        int ch = controlHeight();
        int visible = Math.min(8, values.length);
        int listH = visible * 14 + 2;
        int listX = cx;
        int listY = cy + ch;
        if (mouseX < listX || mouseX >= listX + cw || mouseY < listY || mouseY >= listY + listH) return false;
        int max = Math.max(0, (values.length - visible) * 14);
        listScroll = (float) Math.max(0, Math.min(max, listScroll - delta * 14));
        return true;
    }

    private static String prettify(String name) {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            if (i > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private int currentIndex() {
        E current = option.get();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) return i;
        }
        return 0;
    }
}
