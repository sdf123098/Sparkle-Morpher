package com.micaftic.morpher.client.gui;

final class ModelPanelLayout {
    final int left;
    final int top;
    final int width;
    final int height;
    final int tabHeight;
    final int footerHeight;
    final boolean verticalTabs;
    final int railWidth;
    final int contentLeft;
    final int contentTop;
    final int contentWidth;
    final int contentHeight;
    final int footerTop;

    private ModelPanelLayout(int left, int top, int width, int height, boolean tight, boolean verticalTabs) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        this.verticalTabs = verticalTabs;
        this.footerHeight = tight ? 18 : 24;
        if (verticalTabs) {
            this.tabHeight = 0;
            this.railWidth = 26;
            this.contentLeft = left + this.railWidth + 6;
            this.contentTop = top + 8;
            this.contentWidth = width - this.railWidth - 16;
            this.contentHeight = height - this.footerHeight - 14;
            this.footerTop = top + height - this.footerHeight;
        } else {
            this.tabHeight = tight ? 22 : 28;
            this.railWidth = 0;
            this.contentLeft = left + 10;
            this.contentTop = top + this.tabHeight + 10;
            this.contentWidth = width - 20;
            this.contentHeight = height - this.tabHeight - this.footerHeight - 20;
            this.footerTop = top + height - this.footerHeight;
        }
    }

    static ModelPanelLayout create(int screenWidth, int screenHeight) {
        int marginX = screenWidth >= 760 ? 32 : 12;
        int marginY = screenHeight >= 420 ? 28 : 12;
        int maxWidth = Math.max(1, screenWidth - marginX);
        int maxHeight = Math.max(1, screenHeight - marginY);
        int panelWidth = clamp(screenWidth - marginX, Math.min(520, maxWidth), Math.min(920, maxWidth));
        int panelHeight = clamp(screenHeight - marginY, Math.min(300, maxHeight), Math.min(540, maxHeight));
        double guiScale = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        boolean verticalTabs = guiScale >= 3.0 || screenHeight < 300 || screenWidth < 430;
        boolean tight = verticalTabs || screenHeight < 400 || screenWidth < 560;
        return new ModelPanelLayout((screenWidth - panelWidth) / 2, (screenHeight - panelHeight) / 2, panelWidth, panelHeight, tight, verticalTabs);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
