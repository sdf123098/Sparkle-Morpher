package com.micaftic.morpher.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void nestedSettingsKeepTheSameTopAndBottomAsPrimaryNavigation() {
        for (int width : new int[]{320, 600, 900}) {
            var columns = SettingsPanelLayout.columns(10, 30, width, 310);
            var sections = SettingsPanelLayout.sections(columns);
            assertEquals(columns.top(), sections.top());
            assertEquals(columns.height(), sections.height());
        }
    }

    @Test
    void cloudListsNeverOccupyCategoryNavigationOrOverlapDetails() {
        for (int width : new int[]{320, 600, 900}) {
            var sections = SettingsPanelLayout.sections(SettingsPanelLayout.columns(10, 30, width, 310));
            var panels = SettingsPanelLayout.cloudContent(sections, false);
            assertTrue(panels.listX() >= sections.detailsX());
            assertTrue(panels.detailX() >= sections.detailsX());
            assertTrue(panels.listX() + panels.listWidth() <= panels.detailX()
                    || panels.listY() + panels.listHeight() <= panels.detailY());
            assertTrue(panels.detailX() + panels.detailWidth() <= sections.detailsX() + sections.detailsWidth());
            assertTrue(panels.detailY() + panels.detailHeight() <= sections.top() + sections.height());
            var performance = SettingsPanelLayout.cloudContent(sections, true);
            assertEquals(0, performance.listWidth());
            assertEquals(sections.detailsX(), performance.detailX());
            assertEquals(sections.detailsWidth(), performance.detailWidth());
        }
    }
}
