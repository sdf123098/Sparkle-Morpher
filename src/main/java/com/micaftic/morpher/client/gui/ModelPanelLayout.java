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
        int panelWidth = clamp(screenWidth - 32, 520, 920);
        int panelHeight = clamp(screenHeight - 28, 300, 540);
        return new ModelPanelLayout((screenWidth - panelWidth) / 2, (screenHeight - panelHeight) / 2, panelWidth, panelHeight);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
