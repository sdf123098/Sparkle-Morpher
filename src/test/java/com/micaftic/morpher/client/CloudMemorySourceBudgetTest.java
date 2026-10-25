package com.micaftic.morpher.client;

import com.micaftic.morpher.core.model.ModelSourceType;
import com.micaftic.morpher.core.model.catalog.LocalModelCatalog;
import com.micaftic.morpher.util.DigestUtil;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CloudMemorySourceBudgetTest {
    private static LocalModelCatalog.Entry source(int size) {
        byte[] bytes = new byte[size];
        return LocalModelCatalog.Entry.memory(bytes, DigestUtil.sha256Hex(bytes), "model.ysm", false,
                LocalModelCatalog.fingerprint(bytes), null, null, ModelSourceType.CLOUD);
    }

    @Test
    void evictsLeastRecentlyUsedUnpinnedBytesAndDropsReferencesFromOldPlaceholders() {
        var budget = new CloudMemorySourceBudget(12);
        var evicted = new ArrayList<String>();
        var a = source(4); var b = source(4); var c = source(4);
        for (var entry : java.util.Map.of("a", a, "b", b, "c", c).entrySet())
            assertTrue(budget.retain(entry.getKey(), entry.getValue(), id -> false, (id, ignored) -> evicted.add(id)));
        budget.accessed("a", true); budget.accessed("b", true); budget.accessed("c", true);
        assertTrue(budget.retain("d", source(4), id -> false, (id, ignored) -> evicted.add(id)));
        assertEquals(List.of("a"), evicted);
        assertNull(a.memoryBytes, "stale lazy assemblies must not keep the evicted byte array alive");
        assertEquals(12, budget.stats().bytes());
        assertEquals(1, budget.stats().evictions());
    }

    @Test
    void pinnedSourcesStayAndRetentionRefusesToExceedTheHardBudget() {
        var budget = new CloudMemorySourceBudget(8);
        var a = source(4); var b = source(4);
        assertTrue(budget.retain("a", a, id -> true, (id, ignored) -> fail()));
        assertTrue(budget.retain("b", b, id -> true, (id, ignored) -> fail()));
        assertFalse(budget.retain("c", source(4), id -> true, (id, ignored) -> fail()));
        assertEquals(8, budget.stats().bytes());
        assertNotNull(a.memoryBytes); assertNotNull(b.memoryBytes);
        budget.clear(); assertEquals(0, budget.stats().bytes());
        assertNull(a.memoryBytes); assertNull(b.memoryBytes);
    }

    @Test
    void repeatedRetainReplacesOldEntryWithoutDoubleCountingOrKeepingOldBytes() {
        var budget = new CloudMemorySourceBudget(8);
        var old = source(4); var next = source(6);
        assertTrue(budget.retain("a", old, id -> false, (id, ignored) -> fail()));
        assertTrue(budget.retain("a", next, id -> false, (id, ignored) -> fail()));
        assertNull(old.memoryBytes); assertEquals(6, budget.stats().bytes());
        budget.remove("a"); assertEquals(0, budget.stats().bytes()); assertNull(next.memoryBytes);
    }

    @Test
    void hundredsOfModelsStayBoundedAndProtectedModelsSurvive() {
        var budget = new CloudMemorySourceBudget(16);
        var pinned = source(4);
        budget.retain("selected", pinned, "selected"::equals, (id, ignored) -> fail());
        for (int i = 0; i < 200; i++) {
            budget.retain("model-" + i, source(4), "selected"::equals, (id, ignored) -> assertNotEquals("selected", id));
            assertTrue(budget.stats().bytes() <= 16);
        }
        assertNotNull(pinned.memoryBytes);
        assertEquals(197, budget.stats().evictions());
    }
}
