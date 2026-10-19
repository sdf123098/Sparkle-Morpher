package com.micaftic.morpher.client.entity;
import org.junit.jupiter.api.Test;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
class PreviewEntityIdsTest {
    @Test void previewIdsAreNonzeroNegativeAndUniqueAcrossConcurrentCards(){
        int[] ids=IntStream.range(0,4096).parallel().map(i->PreviewEntityIds.next()).toArray();
        assertTrue(IntStream.of(ids).allMatch(id->id<0));
        assertEquals(ids.length,IntStream.of(ids).distinct().count());
    }
}
