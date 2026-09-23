package com.micaftic.morpher.client.gui;

/** Pure pagination math shared by the Cloud management screens. */
final class CloudScreenPagination {
    private CloudScreenPagination() {}

    static Range range(int itemCount, int pageSize, int requestedPage) {
        if (itemCount < 0) throw new IllegalArgumentException("itemCount must not be negative");
        if (pageSize < 1) throw new IllegalArgumentException("pageSize must be positive");
        int pageCount = itemCount == 0 ? 1 : (itemCount - 1) / pageSize + 1;
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int start = Math.min(itemCount, page * pageSize);
        return new Range(start, Math.min(itemCount, start + pageSize), page, pageCount);
    }

    record Range(int startInclusive, int endExclusive, int page, int pageCount) {}
}
