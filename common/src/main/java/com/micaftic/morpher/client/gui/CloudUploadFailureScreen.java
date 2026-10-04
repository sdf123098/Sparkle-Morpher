package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.gui.button.FlatColorButton;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Readable, scrollable diagnostics for every failed item in a batch. */
public final class CloudUploadFailureScreen extends Screen {
    public record Failure(String modelId, String fileName, Component reason) { }
    private final Screen parent;
    private final List<Failure> failures;
    private int index, scroll;

    public CloudUploadFailureScreen(Screen parent, List<Failure> failures, String selectedId) {
        super(label("failure_details"));
        this.parent = parent;
        this.failures = List.copyOf(failures);
        if (failures.isEmpty()) throw new IllegalArgumentException("No upload failures");
        for (int i = 0; i < failures.size(); i++) if (failures.get(i).modelId().equals(selectedId)) index = i;
    }
    private static Component label(String key) { return Component.translatable("gui.sparkle_morpher.cloud_upload." + key); }
    private int panelW() { return Math.min(700, width - 16); }
    private int panelH() { return Math.min(440, height - 16); }
    private int panelX() { return (width - panelW()) / 2; }
    private int panelY() { return (height - panelH()) / 2; }
    private Component details() {
        Failure failure = failures.get(index);
        return Component.literal(failure.modelId() + "\n" + failure.fileName() + "\n\n").append(failure.reason());
    }
    @Override protected void init() {
        clearWidgets();
        int x = panelX() + 12, y = panelY() + panelH() - 30, w = panelW() - 24;
        FlatColorButton previous = addRenderableWidget(new FlatColorButton(x, y, 24, 20, Component.literal("‹"), button -> {
            index--; scroll = 0; init();
        }));
        previous.active = index > 0;
        FlatColorButton next = addRenderableWidget(new FlatColorButton(x + 28, y, 24, 20, Component.literal("›"), button -> {
            index++; scroll = 0; init();
        }));
        next.active = index + 1 < failures.size();
        addRenderableWidget(new FlatColorButton(x + w - 174, y, 104, 20, label("copy_details"),
                button -> Minecraft.getInstance().keyboardHandler.setClipboard(details().getString())));
        addRenderableWidget(new FlatColorButton(x + w - 64, y, 64, 20, Component.translatable("gui.done"), button -> onClose()));
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = panelX(), y = panelY(), w = panelW(), h = panelH();
        g.fill(0, 0, width, height, 0xEE0A1417);
        g.fill(x, y, x + w, y + h, 0xFF19282D);
        g.fill(x, y, x + 3, y + h, 0xFFFF9990);
        g.drawString(font, title, x + 12, y + 12, 0xFFE9E3D5);
        String page = (index + 1) + " / " + failures.size();
        g.drawString(font, page, x + w - 12 - font.width(page), y + 12, 0xFFA1B4B2);
        var lines = font.split(details(), w - 40);
        int visible = Math.max(1, (h - 80) / 12);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - visible)));
        g.enableScissor(x + 12, y + 34, x + w - 12, y + h - 40);
        for (int i = 0; i < visible && scroll + i < lines.size(); i++)
            g.drawString(font, lines.get(scroll + i), x + 18, y + 38 + i * 12, 0xFFE9E3D5);
        g.disableScissor();
        if (lines.size() > visible) {
            int track = h - 80, thumb = Math.max(8, track * visible / lines.size());
            int top = y + 38 + (track - thumb) * scroll / Math.max(1, lines.size() - visible);
            g.fill(x + w - 17, top, x + w - 15, top + thumb, 0xFF79D7BE);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy != 0 && mx >= panelX() && mx < panelX() + panelW() && my >= panelY() + 34 && my < panelY() + panelH() - 40) {
            scroll = Math.max(0, scroll + (sy > 0 ? -3 : 3)); return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }
    @Override public void onClose() { InputUtil.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
