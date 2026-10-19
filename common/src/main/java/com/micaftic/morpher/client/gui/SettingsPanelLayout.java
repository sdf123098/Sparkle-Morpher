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

    static Columns columns(int x, int y, int width, int height) {
        int safeWidth = Math.max(1, width), safeHeight = Math.max(1, height);
        int gap = Math.min(16, safeWidth / 20);
        int navigationWidth = safeWidth < 600 ? 94 : 120;
        navigationWidth = Math.min(navigationWidth, Math.max(1, safeWidth / 2 - gap));
        int detailX = x + navigationWidth + gap;
        return new Columns(x, y, navigationWidth, detailX,
                Math.max(1, safeWidth - navigationWidth - gap), safeHeight);
    }

    record Columns(int navigationX, int top, int navigationWidth, int detailX, int detailWidth, int height) {}

    static Sections sections(Columns columns) {
        int gap = Math.min(8, columns.detailWidth() / 24);
        int optionsWidth = Math.min(180, Math.max(64, (columns.detailWidth() - gap) / 3));
        optionsWidth = Math.min(optionsWidth, Math.max(1, columns.detailWidth() / 2));
        int detailsX = columns.detailX() + optionsWidth + gap;
        return new Sections(columns.detailX(), optionsWidth, detailsX,
                Math.max(1, columns.detailWidth() - optionsWidth - gap), columns.top(), columns.height());
    }

    /** Account lists live inside content, separately from the category navigation. */
    static AccountPanelLayout cloudContent(Sections sections, boolean performance) {
        int x = sections.detailsX(), y = sections.top(), w = sections.detailsWidth(), h = sections.height();
        if (performance) return new AccountPanelLayout(x, y, 0, 0, x, y, w, h);
        int gap = Math.min(8, w / 24);
        if (w >= 420) {
            int listWidth = Math.min(160, w / 3);
            return new AccountPanelLayout(x, y, listWidth, h, x + listWidth + gap, y, w - listWidth - gap, h);
        }
        int listHeight = Math.min(112, Math.max(1, h / 3));
        return new AccountPanelLayout(x, y, w, listHeight, x, y + listHeight + gap, w, Math.max(1, h - listHeight - gap));
    }

    record Sections(int optionsX, int optionsWidth, int detailsX, int detailsWidth, int top, int height) {}
}
