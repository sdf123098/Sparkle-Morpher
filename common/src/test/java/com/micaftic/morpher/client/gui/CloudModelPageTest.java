package com.micaftic.morpher.client.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CloudModelPageTest {
    @Test void cardsExposeEveryLoadedEntryIncludingTheTail() {
        int cursor = 0, seen = 0;
        do {
            CloudModelPage page = CloudModelPage.cards(83, 12, cursor);
            assertEquals(seen, page.start());
            seen = page.end();
            if (!page.hasNext()) break;
            cursor = page.next();
        } while (true);
        assertEquals(83, seen);
        assertEquals(72, CloudModelPage.cards(83, 12, 999).start());
    }
    @Test void classicGridScrollAndPageButtonsStayWithinRows() {
        var first = CloudModelPage.rows(40, 3, 4, 0);
        assertEquals(0, first.start());
        assertEquals(12, first.end());
        assertEquals(4, first.next());
        var last = CloudModelPage.rows(40, 3, 4, 999);
        assertEquals(30, last.start());
        assertEquals(40, last.end());
        assertFalse(last.hasNext());
        assertEquals(6, last.previous());
    }
    @Test void appendedResultsFillTheVisibleTailBeforeAdvancing() {
        var tail = CloudModelPage.cards(40, 12, 3);
        assertEquals(36, tail.start());
        assertEquals(40, tail.end());
        var appended = CloudModelPage.cards(80, 12, tail.scroll());
        assertEquals(36, appended.start());
        assertEquals(48, appended.end());
        assertEquals(48, CloudModelPage.cards(80, 12, appended.next()).start());
    }
    @Test void emptyCatalogAndResizedAreaClampSafely() {
        var empty = CloudModelPage.cards(0, 12, 99);
        assertEquals(0, empty.start());
        assertEquals(0, empty.end());
        assertEquals(0, empty.scroll());
        assertFalse(empty.hasNext());
        assertEquals(0, CloudModelPage.rows(10, 4, 4, -5).scroll());
        assertEquals(1, CloudModelPage.cards(80, 48, 5).scroll());
    }
}
