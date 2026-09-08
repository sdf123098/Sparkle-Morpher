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
        // 面板随逻辑分辨率按比例放大:大屏留比例边距,小屏几乎全屏;硬顶去掉后超大屏能装更多内容。
        int marginX = screenWidth >= 760 ? clamp(screenWidth / 24, 28, 80) : 12;
        int marginY = screenHeight >= 460 ? clamp(screenHeight / 24, 24, 60) : 12;
        int availW = Math.max(1, screenWidth - marginX);
        int availH = Math.max(1, screenHeight - marginY);
        int panelWidth = clamp(availW, Math.min(520, availW), Math.min(1600, availW));
        int panelHeight = clamp(availH, Math.min(300, availH), Math.min(900, availH));
        // 竖向选项卡只看逻辑宽高:极窄/极矮才竖排。guiScale 是物理/逻辑比值,与逻辑空间无关,不再参与。
        boolean verticalTabs = screenHeight < 300 || screenWidth < 430;
        boolean tight = verticalTabs || screenHeight < 400 || screenWidth < 560;
        return new ModelPanelLayout((screenWidth - panelWidth) / 2, (screenHeight - panelHeight) / 2, panelWidth, panelHeight, tight, verticalTabs);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
