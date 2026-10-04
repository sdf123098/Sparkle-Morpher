package com.micaftic.morpher.client.gui;

/** One visible slice of an incrementally loaded Cloud catalog. */
record CloudModelPage(int scroll, int start, int end, int maxScroll, int step) {
    static CloudModelPage cards(int count, int capacity, int requestedPage) {
        count = Math.max(0, count);
        capacity = Math.max(1, capacity);
        int lastPage = count == 0 ? 0 : (count - 1) / capacity;
        int page = Math.max(0, Math.min(lastPage, requestedPage));
        int start = page * capacity;
        return new CloudModelPage(page, start, (int) Math.min(count, (long) start + capacity), lastPage, 1);
    }
    static CloudModelPage rows(int count, int columns, int visibleRows, int requestedRow) {
        count = Math.max(0, count);
        columns = Math.max(1, columns);
        visibleRows = Math.max(1, visibleRows);
        int totalRows = count == 0 ? 0 : 1 + (count - 1) / columns;
        int lastRow = Math.max(0, totalRows - visibleRows);
        int row = Math.max(0, Math.min(lastRow, requestedRow));
        int start = row * columns;
        return new CloudModelPage(row, start, (int) Math.min(count, (long) start + (long) columns * visibleRows), lastRow, visibleRows);
    }
    boolean hasNext() { return scroll < maxScroll; }
    int next() { return (int) Math.min(maxScroll, (long) scroll + step); }
    int previous() { return Math.max(0, scroll - step); }
}
