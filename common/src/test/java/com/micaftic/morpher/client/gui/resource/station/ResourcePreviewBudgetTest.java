package com.micaftic.morpher.client.gui.resource.station;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;

class ResourcePreviewBudgetTest {
    @Test void failedMirrorBytesStillConsumeThePageBudget() {
        var budget = new ResourcePreviewBudget(100);
        var cancellation = new DownloadCancellation();
        var listener = budget.listener(cancellation);
        listener.onCandidate("a", 1, 2);
        listener.onProgress(40, 80);
        listener.onCandidate("b", 2, 2);
        listener.onProgress(50, 80);
        assertEquals(90, budget.transferredBytes());
        assertThrows(CancellationException.class, () -> listener.onProgress(61, 80));
        assertTrue(cancellation.isCancelled());
        assertTrue(budget.exhausted());
    }

    @Test void independentCardsShareOnePageLimit() {
        var budget = new ResourcePreviewBudget(100);
        var first = budget.listener(new DownloadCancellation());
        var secondCancellation = new DownloadCancellation();
        var second = budget.listener(secondCancellation);
        first.onProgress(70, -1);
        assertThrows(CancellationException.class, () -> second.onProgress(31, -1));
        assertTrue(secondCancellation.isCancelled());
    }

    @Test void duplicateProgressNotificationsDoNotCountBytesTwice() {
        var budget = new ResourcePreviewBudget(100);
        var listener = budget.listener(new DownloadCancellation());
        listener.onProgress(40, -1);
        listener.onProgress(40, -1);
        listener.onProgress(50, -1);
        assertEquals(50, budget.transferredBytes());
    }

    @Test void cancellingThePageCancelsTheRegisteredConnection() {
        var budget = new ResourcePreviewBudget(100);
        var cancellation = new DownloadCancellation();
        var called = new java.util.concurrent.atomic.AtomicInteger();
        budget.listener(cancellation).onCancel(called::incrementAndGet);
        cancellation.cancel();
        assertEquals(1, called.get());
    }
}
