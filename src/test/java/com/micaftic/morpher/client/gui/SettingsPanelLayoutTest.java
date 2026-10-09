package com.micaftic.morpher.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsPanelLayoutTest {

    @Test
    void windowShowsRowsWithinAvailableHeightAndClampsScroll() {
        SettingsPanelLayout.Window window = SettingsPanelLayout.window(10, 150, 30, 20, 12, 99);

        assertEquals(5, window.visibleRows());
        assertEquals(7, window.maxScroll());
        assertEquals(7, window.firstRow());
        assertEquals(12, window.endRowExclusive());
    }

    @Test
    void shortViewportStillShowsOneRowAndEmptyListHasNoScroll() {
        SettingsPanelLayout.Window window = SettingsPanelLayout.window(40, 42, 20, 22, 0, 5);

        assertEquals(1, window.visibleRows());
        assertEquals(0, window.maxScroll());
        assertEquals(0, window.firstRow());
        assertEquals(0, window.endRowExclusive());
    }

    @Test
    void shortListsStartAtTopAndFullyFit() {
        SettingsPanelLayout.Window window = SettingsPanelLayout.window(0, 200, 20, 22, 3, -4);

        assertEquals(0, window.firstRow());
        assertEquals(3, window.endRowExclusive());
        assertEquals(0, window.maxScroll());
    }
}
