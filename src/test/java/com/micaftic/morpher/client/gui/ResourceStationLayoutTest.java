package com.micaftic.morpher.client.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceStationLayoutTest {
    @Test void catalogControlsAndCardsStayInsideTheShellAtCommonGuiScales() {
        for (int[] size : new int[][]{{320, 180}, {427, 239}, {480, 270}, {640, 360}, {960, 540}, {320, 568}}) {
            var shell = ModelPanelLayout.create(size[0], size[1]);
            var page = ResourceStationLayout.catalog(shell.contentLeft, shell.top, shell.contentWidth, shell.footerTop);
            var controls = new java.util.ArrayList<ResourceStationLayout.Rect>();
            page.tools().forEach(tool -> controls.add(tool.bounds()));
            controls.add(page.search()); controls.addAll(page.selection());
            for (var r : controls) {
                assertTrue(r.x() >= shell.contentLeft && r.right() <= shell.contentLeft + shell.contentWidth);
                assertTrue(r.bottom() <= page.cards().y(), "controls must reserve their own space above the cards");
            }
            for (int i = 0; i < controls.size(); i++) for (int j = i + 1; j < controls.size(); j++)
                assertFalse(controls.get(i).intersects(controls.get(j)));
            for (int i = 0; i < page.grid().capacity(); i++) {
                var card = page.grid().card(page.cards(), i);
                assertTrue(card.right() <= page.cards().right());
                assertTrue(card.bottom() < page.pagination().y());
            }
        }
    }

    @Test void catalogueHasBothNetworkModesAndIconEntrancesWithoutACatalogueTab() {
        var page = ResourceStationLayout.catalog(42, 20, 375, 250);
        assertEquals(java.util.List.of(ResourceStationLayout.CatalogAction.MAINLAND, ResourceStationLayout.CatalogAction.GLOBAL,
                ResourceStationLayout.CatalogAction.QUEUE, ResourceStationLayout.CatalogAction.SOURCES,
                ResourceStationLayout.CatalogAction.REFRESH, ResourceStationLayout.CatalogAction.PREVIEWS,
                ResourceStationLayout.CatalogAction.OPTIONS), page.tools().stream().map(ResourceStationLayout.Tool::action).toList());
        assertTrue(page.tools().stream().allMatch(tool -> tool.bounds().width() == 22), "Navigation and mode selectors must use compact icon targets");
        assertTrue(page.cards().y() < 100, "Removing the redundant catalogue tabs must recover vertical room for cards");
    }

    @Test void wideCatalogKeepsItsGridBesidePreviewAndCompactCatalogRetainsPopupSpace() {
        for (int width : new int[]{200, 375, 428, 574, 894, 1200}) {
            var page = ResourceStationLayout.catalog(42, 20, width, 500);
            var pane = page.details();
            boolean inline = com.micaftic.morpher.core.gui.ResponsiveUiLayout.inlinePreview(width, page.cards().height());
            assertEquals(inline, pane.width() > 0);
            if (inline) {
                assertFalse(page.cards().intersects(pane));
                assertEquals(page.cards().right() + 4, pane.x());
                assertEquals(42 + width, pane.right());
                assertEquals(page.cards().y(), pane.y());
                assertEquals(page.cards().bottom(), pane.bottom());
                assertTrue(pane.width() >= 120);
            } else assertEquals(width, page.cards().width());
            assertEquals(page.cards().width(), page.pagination().width());
        }
    }

    @Test void pagingReachesEveryResourceExactlyOnceAndHandlesEmptyResults() {
        var grid = ResourceStationLayout.grid(428, 130);
        assertEquals(1, grid.pages(0)); assertEquals(0, grid.first(99, 0)); assertEquals(0, grid.end(99, 0));
        int next = 0;
        for (int page = 0; page < grid.pages(37); page++) {
            assertEquals(next, grid.first(page, 37)); next = grid.end(page, 37);
        }
        assertEquals(37, next);
    }

    @Test void queueRowsAndCancelAllControlsFitCompactScreens() {
        for (int[] size : new int[][]{{320, 180}, {427, 239}, {480, 270}, {960, 540}}) {
            var shell = ModelPanelLayout.create(size[0], size[1]);
            var queue = ResourceStationLayout.queue(shell.contentLeft, shell.top, shell.contentWidth, shell.footerTop);
            assertEquals(2, queue.actions().size());
            for (var action : queue.actions()) assertTrue(action.right() <= shell.contentLeft + shell.contentWidth);
            assertTrue(queue.rows() >= 1);
            for (int i = 0; i < queue.rows(); i++) assertTrue(queue.row(i).bottom() < queue.pagination().y());
        }
    }

    @Test void cancelledTasksOfferRetryAndUploadsCannotCancelAnUnrelatedDownload() {
        assertEquals(ResourceStationLayout.TaskAction.RETRY, ResourceStationLayout.taskAction("CANCELLED"));
        assertEquals(ResourceStationLayout.TaskAction.RETRY, ResourceStationLayout.taskAction("FAILED"));
        assertEquals(ResourceStationLayout.TaskAction.NONE, ResourceStationLayout.taskAction("UPLOADING"));
        assertEquals(ResourceStationLayout.TaskAction.OPEN, ResourceStationLayout.taskAction("DONE"));
        for (String state : new String[]{"QUEUED", "DOWNLOADING", "IMPORTING"})
            assertEquals(ResourceStationLayout.TaskAction.CANCEL, ResourceStationLayout.taskAction(state));
    }
    @Test void textActionsWrapInsteadOfDisappearingPastTheRightEdge() {
        for (int width : new int[]{200, 230, 274, 375, 428}) {
            var buttons = ResourceStationLayout.flow(42, 78, width, 20, 52, 44, 56, 32, 76);
            assertEquals(5, buttons.size());
            for (var button : buttons) {
                assertTrue(button.x() >= 42);
                assertTrue(button.right() <= 42 + width, "every selection/download action must remain visible");
            }
            for (int i = 0; i < buttons.size(); i++) for (int j = i + 1; j < buttons.size(); j++)
                assertFalse(buttons.get(i).intersects(buttons.get(j)), "wrapped actions must not overlap");
        }
    }
    @Test void resourceCardsUseTheSameDimensionsAndOriginsAsStandardModelCards() {
        for (int[] size : new int[][]{{200, 60}, {375, 100}, {428, 130}, {574, 220}, {894, 390}}) {
            var standard = ModelPickerLayout.cards(size[0], size[1]);
            var grid = ResourceStationLayout.grid(size[0], size[1]);
            assertEquals(standard.cols(), grid.columns());
            assertEquals(standard.rows(), grid.rows());
            assertEquals(standard.cellW(), grid.cardWidth(), "Repository cards must use the standard portrait width");
            assertEquals(standard.cellH(), grid.cardHeight(), "Card artwork must keep its standard aspect ratio");
            assertEquals(ModelPickerLayout.CARD_GAP, grid.gap());
            var bounds = new ResourceStationLayout.Rect(42, 78, size[0], size[1]);
            for (int i = 0; i < grid.capacity(); i++) {
                var card = grid.card(bounds, i);
                assertEquals(42 + standard.originX(size[0]) + standard.columnX(size[0], i % standard.cols()), card.x());
                assertEquals(78 + standard.originY(size[1]) + (i / standard.cols()) * (standard.cellH() + ModelPickerLayout.CARD_GAP), card.y());
                assertTrue(card.right() <= bounds.right());
                assertTrue(card.bottom() <= bounds.bottom());
            }
        }
    }
}
