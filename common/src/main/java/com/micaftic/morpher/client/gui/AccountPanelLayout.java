package com.micaftic.morpher.client.gui;

/** Shared two-column geometry for the account, instance and scope pages. */
public record AccountPanelLayout(int listX, int listY, int listWidth, int listHeight,
                                 int detailX, int detailY, int detailWidth, int detailHeight) {
    public static AccountPanelLayout of(int contentLeft, int contentTop, int contentWidth, int contentHeight) {
        int x = contentLeft + 8;
        int y = contentTop + 38;
        int width = Math.max(200, contentWidth - 16);
        int height = Math.max(100, contentHeight - 66);
        int gap = 10;
        int listWidth = Math.max(110, Math.min(width - 120 - gap, width * 42 / 100));
        int detailX = x + listWidth + gap;
        return new AccountPanelLayout(x, y, listWidth, height,
                detailX, y, width - listWidth - gap, height);
    }

    public int listRows() {
        return Math.max(1, (listHeight - 48) / 36);
    }

    public int fieldX() {
        return detailX + 12;
    }

    public int fieldWidth() {
        return Math.max(80, Math.min(460, detailWidth - 24));
    }

    public int fieldY(int index) {
        return detailY + 66 + index * (detailHeight < 260 ? 30 : 40);
    }

    public int actionY(int fields) {
        return fieldY(fields - 1) + 30;
    }

    public int buttonWidth(int columns) {
        return Math.max(40, Math.min(210, (fieldWidth() - (columns - 1) * 6) / columns));
    }
}
