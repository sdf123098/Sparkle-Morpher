package com.micaftic.morpher.client.gui;

/** Embedded details yield space to the grid; the full texture picker remains available. */
record CloudModelViewport(int gridHeight, int detailHeight) {
    static CloudModelViewport measure(int availableHeight, boolean compact, boolean expanded) {
        int room = Math.max(1, availableHeight);
        int desired = compact ? (expanded ? 112 : 18) : 0;
        int details = Math.min(desired, Math.max(0, room - 23));
        if (details < 18) details = 0;
        return new CloudModelViewport(Math.max(1, room - (details > 0 ? details + 3 : 0)), details);
    }
}
