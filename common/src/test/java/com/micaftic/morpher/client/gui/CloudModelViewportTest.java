package com.micaftic.morpher.client.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CloudModelViewportTest {
    @Test void compactExpandedDetailsNeverOverlapFooterAtCommonGuiScales() {
        for (int[] size : new int[][]{{480,270}, {320,180}, {640,360}, {960,540}, {1920,1080}}) {
            var layout = ModelPanelLayout.create(size[0], size[1]);
            int gridY = layout.contentTop + 8 + (layout.contentHeight < 185 ? 66 : 86) + 20;
            int footerTop = layout.footerTop - 6 - 28;
            int room = footerTop - 4 - gridY;
            var viewport = CloudModelViewport.measure(room, true, true);
            assertTrue(viewport.gridHeight() >= 1);
            assertTrue(viewport.gridHeight() + viewport.detailHeight() + (viewport.detailHeight() > 0 ? 3 : 0) <= Math.max(1, room));
            assertTrue(gridY + viewport.gridHeight() + viewport.detailHeight() + (viewport.detailHeight() > 0 ? 3 : 0) <= footerTop - 4);
            assertTrue(viewport.detailHeight() <= 112);
        }
    }
    @Test void desktopKeepsGridSpaceAndExpandedDetailUsesOnlyAvailableRoom() {
        assertEquals(new CloudModelViewport(200, 0), CloudModelViewport.measure(200, false, false));
        assertEquals(new CloudModelViewport(85, 112), CloudModelViewport.measure(200, true, true));
        assertEquals(new CloudModelViewport(20, 21), CloudModelViewport.measure(44, true, true));
    }
}
