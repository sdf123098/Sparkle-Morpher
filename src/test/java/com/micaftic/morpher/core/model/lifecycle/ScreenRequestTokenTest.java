package com.micaftic.morpher.core.model.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenRequestTokenTest {
    @Test
    void anOlderResourceRequestCannotOverwriteTheNewerRequest() {
        ScreenRequestToken older = new ScreenRequestToken(1, 4L);
        ScreenRequestToken newer = new ScreenRequestToken(2, 4L);

        assertFalse(older.isCurrent(newer.requestId(), newer.screenGeneration()));
        assertTrue(newer.isCurrent(newer.requestId(), newer.screenGeneration()));
    }

    @Test
    void aResourceRequestCannotWriteIntoAReopenedScreen() {
        ScreenRequestToken request = new ScreenRequestToken(3, 7L);

        assertFalse(request.isCurrent(3, 8L));
    }
}
