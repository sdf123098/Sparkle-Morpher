package com.micaftic.morpher.client.gui;

/** Resource Station toolbar and queue geometry; model cards share the standard picker layout. */
public final class ResourceStationLayout {
    private ResourceStationLayout() {}
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean intersects(Rect other) {
            return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }
    }
    public static java.util.List<Rect> flow(int left, int top, int width, int height, int... widths) {
        var result = new java.util.ArrayList<Rect>();
        int x = left, y = top;
        for (int buttonWidth : widths) {
            int safeWidth = Math.min(Math.max(1, width), buttonWidth);
            if (x > left && x + safeWidth > left + width) { x = left; y += height + 3; }
            result.add(new Rect(x, y, safeWidth, height));
            x += safeWidth + 3;
        }
        return java.util.List.copyOf(result);
    }
    public record Grid(int columns, int rows, int cardWidth, int cardHeight, int gap) {
        public int capacity() { return columns * rows; }
        public int pages(int count) { return Math.max(1, (Math.max(0, count) + capacity() - 1) / capacity()); }
        public int first(int page, int count) { return Math.min(Math.max(0, count), Math.max(0, Math.min(pages(count) - 1, page)) * capacity()); }
        public int end(int page, int count) { return Math.min(Math.max(0, count), first(page, count) + capacity()); }
        public Rect card(Rect bounds, int slot) {
            return new Rect(bounds.x + (slot % columns) * (cardWidth + gap),
                    bounds.y + (slot / columns) * (cardHeight + gap), cardWidth, cardHeight);
        }
    }
    public enum CatalogAction { MAINLAND, GLOBAL, QUEUE, SOURCES, REFRESH, PREVIEWS, OPTIONS }
    public record Tool(CatalogAction action, Rect bounds) {}
    public record Catalog(java.util.List<Tool> tools, Rect search,
                          java.util.List<Rect> selection, Rect cards, Rect details, Grid grid, Rect pagination) {}
    public static Catalog catalog(int left, int top, int width, int footerTop) {
        int safeWidth = Math.max(1, width), h = footerTop - top < 300 ? 20 : 22;
        boolean compact = safeWidth < 300;
        int toolbarY = top + h + 4, toolsWidth = 7 * 22 + 6 * 3;
        int searchW = compact ? safeWidth : safeWidth - toolsWidth - 3;
        var toolRects = flow(compact ? left : left + searchW + 3, compact ? toolbarY + h + 4 : toolbarY,
                compact ? safeWidth : toolsWidth, h, 22, 22, 22, 22, 22, 22, 22);
        var tools = new java.util.ArrayList<Tool>();
        var actions = CatalogAction.values();
        for (int i = 0; i < actions.length; i++) tools.add(new Tool(actions[i], toolRects.get(i)));
        var selection = flow(left, toolRects.getLast().bottom() + 4, safeWidth, h, 22, 48, 22, 22, 22);
        int gridY = selection.getLast().bottom() + 6;
        int gridHeight = Math.max(1, footerTop - 6 - gridY);
        int listWidth = ModelPickerLayout.listWidth(safeWidth, gridHeight);
        Rect cards = new Rect(left, gridY, listWidth, gridHeight);
        Rect details = new Rect(left + listWidth + 4, gridY, Math.max(0, safeWidth - listWidth - 4), gridHeight);
        return new Catalog(java.util.List.copyOf(tools), new Rect(left, toolbarY, searchW, h),
                selection, cards, details, grid(cards.width, cards.height), new Rect(left, footerTop, listWidth, 20));
    }
    public record Queue(java.util.List<Rect> actions, Rect list, int rowHeight, int rows, Rect pagination) {
        public Rect row(int slot) { return new Rect(list.x, list.y + slot * rowHeight, list.width, rowHeight); }
    }
    public static Queue queue(int left, int top, int width, int footerTop) {
        int h = footerTop - top < 300 ? 20 : 22;
        var actions = flow(left, top + 46, width, h, 22, 22);
        int listY = actions.getLast().bottom() + 8, rowH = 52;
        Rect list = new Rect(left, listY, width, Math.max(0, footerTop - 6 - listY));
        return new Queue(actions, list, rowH, Math.max(0, list.height / rowH), new Rect(left, footerTop, width, 20));
    }
    public enum TaskAction { CANCEL, RETRY, OPEN, NONE }
    public static TaskAction taskAction(String state) {
        return switch (state) {
            case "QUEUED", "DOWNLOADING", "IMPORTING" -> TaskAction.CANCEL;
            case "CANCELLED", "FAILED" -> TaskAction.RETRY;
            case "DONE" -> TaskAction.OPEN;
            default -> TaskAction.NONE;
        };
    }
    public static Grid grid(int width, int height) {
        var standard = ModelPickerLayout.cards(width, height);
        return new Grid(standard.cols(), standard.rows(), standard.cellW(), standard.cellH(), ModelPickerLayout.CARD_GAP);
    }
}
