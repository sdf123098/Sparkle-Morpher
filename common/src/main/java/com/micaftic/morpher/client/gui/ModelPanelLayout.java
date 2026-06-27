package com.micaftic.morpher.client.gui;

final class ModelPanelLayout {
    final int left;
    final int top;
    final int width;
    final int height;
    final int tabHeight;
    final int footerHeight;
    final int contentLeft;
    final int contentTop;
    final int contentWidth;
    final int contentHeight;
    final int footerTop;

    private ModelPanelLayout(int left, int top, int width, int height) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        this.tabHeight = 28;
        this.footerHeight = 24;
        this.contentLeft = left + 10;
        this.contentTop = top + this.tabHeight + 10;
        this.contentWidth = width - 20;
        this.contentHeight = height - this.tabHeight - this.footerHeight - 20;
        this.footerTop = top + height - this.footerHeight;
    }

    static ModelPanelLayout create(int screenWidth, int screenHeight) {
        int marginX = screenWidth >= 760 ? 32 : 12;
        int marginY = screenHeight >= 420 ? 28 : 12;
        int maxWidth = Math.max(1, screenWidth - marginX);
        int maxHeight = Math.max(1, screenHeight - marginY);
        int panelWidth = clamp(screenWidth - marginX, Math.min(520, maxWidth), Math.min(920, maxWidth));
        int panelHeight = clamp(screenHeight - marginY, Math.min(300, maxHeight), Math.min(540, maxHeight));
        return new ModelPanelLayout((screenWidth - panelWidth) / 2, (screenHeight - panelHeight) / 2, panelWidth, panelHeight);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
