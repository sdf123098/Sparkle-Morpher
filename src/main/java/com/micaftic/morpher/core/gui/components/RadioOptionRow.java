package com.micaftic.morpher.core.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import com.micaftic.morpher.core.gui.Option;
import com.micaftic.morpher.core.gui.OptionRow;

import java.awt.*;
import java.util.List;

public class RadioOptionRow extends OptionRow<Integer> {

    private final List<String> labels;
    private boolean open;
    private float listScroll;

    public RadioOptionRow(int x, int y, int width, int height, Option<Integer> option, List<String> labels) {
        super(x, y, width, height, option);
        this.labels = labels;
    }

    @Override
    protected int controlWidth() {
        return Mth.clamp(width / 2, 100, 220);
    }

    @Override
    protected void renderControl(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int cx = controlX();
        int cy = controlY();
        int cw = controlWidth();
        int ch = controlHeight();
        boolean hover = isMouseOverControl(mouseX, mouseY);

        g.fill(cx, cy, cx + cw, cy + ch, blendBg(hover, 0x3EC8C8C8));
        g.outline(cx, cy, cw, ch, 0x60FFFFFF);

        Component text = Component.literal(labelAt(currentIndex()));
        g.text(Minecraft.getInstance().font, text, cx + 6, cy + (ch - 8) / 2, 0xFFFFFFFF, false);

        int arrowX = cx + cw - 10;
        int arrowY = cy + ch / 2 - 1;
        g.fill(arrowX, arrowY, arrowX + 6, arrowY + 1, 0xFFCCCCCC);
        g.fill(arrowX + 1, arrowY + 1, arrowX + 5, arrowY + 2, 0xFFCCCCCC);
        g.fill(arrowX + 2, arrowY + 2, arrowX + 4, arrowY + 3, 0xFFCCCCCC);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean flag) {
        if (!isMouseOverControl(event.x(), event.y())) return;
        open = !open;
        if (open) {
            int cur = currentIndex();
            int firstVisible = (int) (listScroll / 14);
            if (cur < firstVisible || cur >= firstVisible + 8) {
                listScroll = Math.max(0, Math.min(cur, labels.size() - 8)) * 14;
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
    public void renderOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, float scrollDisplay) {
        if (!open || labels.isEmpty()) return;
        int cx = controlX();
        int cw = controlWidth();
        int cy = controlY() - (int) scrollDisplay;
        int ch = controlHeight();
        int visible = Math.min(8, labels.size());
        int listH = visible * 14 + 2;
        int listX = cx;
        int listY = cy + ch;
        g.pose().pushMatrix();
        g.pose().translate(0.0f, 0.0f);
        g.fill(listX, listY, listX + cw, listY + listH, 0xFF111111);
        int first = (int) (listScroll / 14);
        first = Math.max(0, Math.min(first, Math.max(0, labels.size() - visible)));
        for (int i = 0; i < visible; i++) {
            int idx = first + i;
            if (idx >= labels.size()) break;
            int itemY = listY + 1 + i * 14;
            boolean hover = mouseX >= listX && mouseX < listX + cw && mouseY >= itemY && mouseY < itemY + 14;
            boolean selected = idx == currentIndex();
            int bg = selected ? new Color(255,255,255,60).getRGB() : (hover ? 0xFF333333 : 0);
            if (bg != 0) g.fill(listX + 1, itemY, listX + cw - 1, itemY + 14, bg);
            g.text(Minecraft.getInstance().font, Component.literal(labelAt(idx)), listX + 6, itemY + (14 - 8) / 2, -1, true);
        }
        if (labels.size() > visible) {
            int trackX = listX + cw - 3;
            int trackTop = listY + 1;
            int trackBot = listY + listH - 1;
            int trackH = trackBot - trackTop;
            int thumbH = Math.max(8, trackH * visible / labels.size());
            int thumbY = trackTop + (int) ((trackH - thumbH) * listScroll / Math.max(1, (labels.size() - visible) * 14));
            g.fill(trackX, trackTop, trackX + 2, trackBot, 0x80444444);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFFAAAAAA);
        }
        g.pose().popMatrix();
    }

    @Override
    public boolean overlayMouseClicked(double mouseX, double mouseY, int button, float scrollDisplay) {
        if (!open) return false;
        int cx = controlX();
        int cw = controlWidth();
        int cy = controlY() - (int) scrollDisplay;
        int ch = controlHeight();
        int visible = Math.min(8, labels.size());
        int listH = visible * 14 + 2;
        int listX = cx;
        int listY = cy + ch;
        if (mouseX < listX || mouseX >= listX + cw || mouseY < listY || mouseY >= listY + listH) return false;
        int first = (int) (listScroll / 14);
        first = Math.max(0, Math.min(first, Math.max(0, labels.size() - visible)));
        int slot = (int) ((mouseY - listY - 1) / 14);
        int idx = first + slot;
        if (idx >= 0 && idx < labels.size()) {
            option.setPending(idx);
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
        int visible = Math.min(8, labels.size());
        int listH = visible * 14 + 2;
        int listX = cx;
        int listY = cy + ch;
        if (mouseX < listX || mouseX >= listX + cw || mouseY < listY || mouseY >= listY + listH) return false;
        int max = Math.max(0, (labels.size() - visible) * 14);
        listScroll = (float) Math.max(0, Math.min(max, listScroll - delta * 14));
        return true;
    }

    private String labelAt(int idx) {
        if (idx < 0 || idx >= labels.size()) return "";
        return labels.get(idx);
    }

    private int currentIndex() {
        Integer cur = option.get();
        if (cur == null) return 0;
        return Mth.clamp(cur, 0, Math.max(0, labels.size() - 1));
    }
}
