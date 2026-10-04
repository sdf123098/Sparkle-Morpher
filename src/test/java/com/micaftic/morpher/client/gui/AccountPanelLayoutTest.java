package com.micaftic.morpher.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountPanelLayoutTest {
    @Test
    void columnsAndControlsFitBothWideAndNarrowPanels() {
        for (int width : new int[] {320, 500, 1740}) {
            AccountPanelLayout layout = AccountPanelLayout.of(40, 84, width, 650);
            assertTrue(layout.listX() + layout.listWidth() < layout.detailX());
            assertTrue(layout.detailX() + layout.detailWidth() <= 40 + width);
            assertTrue(layout.fieldX() + layout.fieldWidth() <= layout.detailX() + layout.detailWidth());
            assertTrue(layout.buttonWidth(3) * 3 + 12 <= layout.fieldWidth());
            assertTrue(layout.actionY(3) + 20 < layout.detailY() + layout.detailHeight());
        }
    }
}
