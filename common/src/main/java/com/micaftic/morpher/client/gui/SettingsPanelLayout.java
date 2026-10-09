package com.micaftic.morpher.client.gui;

/** Pure row-window calculation for the settings panel. */
final class SettingsPanelLayout {

    private SettingsPanelLayout() {
    }

    static Window window(int contentTop, int contentBottom, int reservedHeight, int rowHeight,
                         int rowCount, int requestedScroll) {
        int safeRowHeight = Math.max(1, rowHeight);
        int safeRowCount = Math.max(0, rowCount);
        int availableHeight = Math.max(0, contentBottom - contentTop - Math.max(0, reservedHeight));
        int visibleRows = Math.max(1, availableHeight / safeRowHeight);
        int maxScroll = Math.max(0, safeRowCount - visibleRows);
        int scroll = Math.max(0, Math.min(maxScroll, requestedScroll));
        return new Window(scroll, Math.min(safeRowCount, scroll + visibleRows), visibleRows, maxScroll);
    }

    record Window(int firstRow, int endRowExclusive, int visibleRows, int maxScroll) {
        int scroll() {
            return firstRow;
        }
    }
}
