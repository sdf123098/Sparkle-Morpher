package com.micaftic.morpher.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloudScreenPaginationTest {
    @Test
    void clampsPageAndReturnsOnlyVisibleItemRange() {
        assertEquals(new CloudScreenPagination.Range(3, 6, 1, 3),
                CloudScreenPagination.range(9, 3, 1));
        assertEquals(new CloudScreenPagination.Range(6, 9, 2, 3),
                CloudScreenPagination.range(9, 3, 99));
    }

    @Test
    void emptyListsHaveOneEmptyPage() {
        assertEquals(new CloudScreenPagination.Range(0, 0, 0, 1),
                CloudScreenPagination.range(0, 5, 0));
    }
}
