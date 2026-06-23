package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.gui.button.IconButton;
import com.micaftic.morpher.client.gui.resource.ResourceDownloadManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DownloadScreen extends Screen {
    private final PlayerModelScreen parentScreen;
    private final Screen resourceStationScreen;
    private int guiLeft;
    private int guiTop;
    private int panelWidth;
    private int panelHeight;

    private static final int ICON_SIZE = 18;
    private static final int FOOTER_HEIGHT = 18;

    public DownloadScreen(PlayerModelScreen modelScreen) {
        this(modelScreen, null);
    }

    public DownloadScreen(PlayerModelScreen modelScreen, Screen resourceStationScreen) {
        super(Component.translatable("gui.sparkle_morpher.resource_station.downloads"));
        this.parentScreen = modelScreen;
        this.resourceStationScreen = resourceStationScreen;
    }

    @Override
    protected void init() {
        clearWidgets();
        this.panelWidth = Math.min(420, this.width - 20);
        this.panelHeight = Math.min(235, this.height - 20);
        this.guiLeft = (this.width - this.panelWidth) / 2;
        this.guiTop = (this.height - this.panelHeight) / 2;

        int footerY = this.guiTop + this.panelHeight - FOOTER_HEIGHT;
        addRenderableWidget(new IconButton(this.guiLeft + 4, footerY, ICON_SIZE, ICON_SIZE, 0, 32, button -> {
            Minecraft.getInstance().setScreen(this.resourceStationScreen == null ? this.parentScreen : this.resourceStationScreen);
        }));
        IconButton clearButton = new IconButton(this.guiLeft + 4 + ICON_SIZE + 2, footerY, ICON_SIZE, ICON_SIZE, 96, 64, button -> ResourceDownloadManager.clearFinished());
        clearButton.setTooltipText("gui.sparkle_morpher.resource_station.clear_finished");
        addRenderableWidget(clearButton);
        ResourceDownloadManager.Snapshot snapshot = ResourceDownloadManager.snapshot();
        IconButton cancelButton = new IconButton(this.guiLeft + 4 + ICON_SIZE * 2 + 4, footerY, ICON_SIZE, ICON_SIZE, 112, 64, button -> {
            ResourceDownloadManager.cancelCurrent();
            init();
        });
        cancelButton.setTooltipText("gui.sparkle_morpher.resource_station.cancel_current");
        cancelButton.active = snapshot.currentTask() != null;
        addRenderableWidget(cancelButton);
    }

    @Override
    public void tick() {
        super.tick();
        ResourceDownloadManager.tick();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(guiGraphics);
        guiGraphics.fill(this.guiLeft, this.guiTop, this.guiLeft + this.panelWidth, this.guiTop + this.panelHeight, 0xE0202020);
        guiGraphics.fill(this.guiLeft, this.guiTop, this.guiLeft + this.panelWidth, this.guiTop + 2, 0xFFB15D2B);
        guiGraphics.drawString(this.font, this.title, this.guiLeft + 10, this.guiTop + 10, 0xFFE9E0D0, false);
        renderTasks(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderTasks(GuiGraphics guiGraphics) {
        ResourceDownloadManager.Snapshot snapshot = ResourceDownloadManager.snapshot();
        int pw = this.panelWidth;
        int y = this.guiTop + 30;
        int statusColor = snapshot.statusColor().getColor() == null ? 0xFFBDBDBD : snapshot.statusColor().getColor();
        guiGraphics.drawString(this.font, snapshot.status(), this.guiLeft + 10, y, statusColor, false);
        y += 16;

        List<ResourceDownloadManager.TaskSnapshot> rows = new ArrayList<>();
        rows.addAll(snapshot.unfinishedTasks());
        rows.addAll(snapshot.finishedTasks().stream().limit(8).toList());
        if (rows.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("gui.sparkle_morpher.resource_station.no_downloads"), this.guiLeft + pw / 2, this.guiTop + 92, 0xFF8F8F8F);
            return;
        }

        int maxRows = Math.min(9, rows.size());
        int nameWidth = Math.max(60, pw - 280);
        for (int i = 0; i < maxRows; i++) {
            ResourceDownloadManager.TaskSnapshot row = rows.get(i);
            int rowY = y + i * 18;
            guiGraphics.fill(this.guiLeft + 10, rowY - 2, this.guiLeft + pw - 10, rowY + 15, i % 2 == 0 ? 0x66313131 : 0x66262626);
            guiGraphics.drawString(this.font, trim(row.name(), nameWidth), this.guiLeft + 14, rowY + 2, 0xFFEDE1CC, false);
            int stateX = this.guiLeft + 14 + nameWidth + 4;
            guiGraphics.drawString(this.font, stateLabel(row.state()), stateX, rowY + 2, stateColor(row.state()), false);
            int barX = stateX + 36;
            int barWidth = Math.min(78, pw - (barX - this.guiLeft) - 100);
            int barY = rowY + 3;
            guiGraphics.fill(barX, barY, barX + barWidth, barY + 6, 0xAA101010);
            int fill = Math.max(0, Math.min(barWidth, (int) (row.progress() * barWidth)));
            guiGraphics.fill(barX, barY, barX + fill, barY + 6, 0xFFB15D2B);
            int msgX = barX + barWidth + 4;
            int msgWidth = Math.max(40, pw - (msgX - this.guiLeft) - 10);
            guiGraphics.drawString(this.font, trim(row.message().getString(), msgWidth), msgX, rowY + 2, 0xFF9FA8A6, false);
        }
    }

    private Component stateLabel(ResourceDownloadManager.TaskState state) {
        return Component.translatable("gui.sparkle_morpher.resource_station.state." + state.name().toLowerCase(Locale.ROOT));
    }

    private int stateColor(ResourceDownloadManager.TaskState state) {
        return switch (state) {
            case DONE -> ChatFormatting.GREEN.getColor();
            case FAILED -> ChatFormatting.RED.getColor();
            case CANCELLED -> ChatFormatting.GRAY.getColor();
            default -> ChatFormatting.YELLOW.getColor();
        };
    }

    private String trim(String value, int maxWidth) {
        if (this.font.width(value) <= maxWidth) {
            return value;
        }
        String ellipsis = "...";
        int keep = value.length();
        while (keep > 0 && this.font.width(value.substring(0, keep) + ellipsis) > maxWidth) {
            keep--;
        }
        return value.substring(0, Math.max(0, keep)) + ellipsis;
    }
}
