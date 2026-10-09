package com.micaftic.morpher.core.model.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ModelScanRevisionTest {
    @Test
    void startingANewerScanMakesTheOlderCandidateStale() {
        ModelScanRevision revisions = new ModelScanRevision();

        long slowScan = revisions.begin();
        long fastScan = revisions.begin();

        assertFalse(revisions.isCurrent(slowScan));
        assertTrue(revisions.isCurrent(fastScan));
    }

    @Test
    void invalidatingSourceStateMakesAnInFlightScanStale() {
        ModelScanRevision revisions = new ModelScanRevision();
        long scan = revisions.begin();

        revisions.invalidate();

        assertFalse(revisions.isCurrent(scan));
    }

    @Test
    void slowerScanCannotFinalizeOverNewerCatalogCandidate() throws Exception {
        ModelScanRevision revisions = new ModelScanRevision();
        AtomicReference<Map<String, String>> catalog = new AtomicReference<>(Map.of("avatar", "previous"));
        CountDownLatch slowCandidateReady = new CountDownLatch(1);
        CountDownLatch allowSlowFinalize = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        long slowRevision = revisions.begin();

        try {
            Future<Boolean> slowFinalize = worker.submit(() -> {
                Map<String, String> staleCandidate = Map.of(); // An old scan would delete avatar.
                slowCandidateReady.countDown();
                assertTrue(allowSlowFinalize.await(5, TimeUnit.SECONDS));
                if (!revisions.isCurrent(slowRevision)) return false;
                catalog.set(staleCandidate);
                return true;
            });

            assertTrue(slowCandidateReady.await(5, TimeUnit.SECONDS));
            long fastRevision = revisions.begin();
            Map<String, String> fastCandidate = Map.of("avatar", "newer");
            assertTrue(revisions.isCurrent(fastRevision));
            catalog.set(fastCandidate);
            allowSlowFinalize.countDown();

            assertFalse(slowFinalize.get(5, TimeUnit.SECONDS));
            assertEquals(Map.of("avatar", "newer"), catalog.get());
        } finally {
            allowSlowFinalize.countDown();
            worker.shutdownNow();
        }
    }
}
