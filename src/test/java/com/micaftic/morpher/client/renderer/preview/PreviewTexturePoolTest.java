package com.micaftic.morpher.client.renderer.preview;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class PreviewTexturePoolTest {
    @Test void recentlyDrawnCardsSurviveAndEvictedTargetsAreReleased() {
        var released = new ArrayList<String>();
        var pool = new PreviewTexturePool<String, String>(2, 100, released::add);
        pool.put("a", "targetA", 40);
        pool.put("b", "targetB", 40);
        assertEquals("targetA", pool.get("a"));
        pool.put("c", "targetC", 40);
        assertNull(pool.get("b"));
        assertEquals(java.util.List.of("targetB"), released);
        assertEquals(80, pool.bytes());
        pool.clear();
        assertEquals(3, released.size());
        assertEquals(0, pool.bytes());
        assertEquals(0, pool.size());
    }

    @Test void memoryLimitAndReplacementReleaseEveryTextureExactlyOnce() {
        var released = new ArrayList<Integer>();
        var pool = new PreviewTexturePool<String, Integer>(32, 100, released::add);
        pool.put("a", 1, 60);
        pool.put("b", 2, 60);
        assertEquals(java.util.List.of(1), released);
        pool.put("b", 3, 20);
        pool.put("oversize", 4, 101);
        assertEquals(java.util.List.of(1, 2, 4), released);
        assertEquals(20, pool.bytes());
        assertEquals(1, pool.size());
        pool.clear();
        assertEquals(java.util.List.of(1, 2, 4, 3), released);
    }
}
