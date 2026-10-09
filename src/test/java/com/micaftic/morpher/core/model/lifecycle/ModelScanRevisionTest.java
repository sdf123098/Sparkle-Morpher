package com.micaftic.morpher.core.model.lifecycle;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    void managerFinalizationGateRejectsOldCandidateAfterNewScanBegins() throws Exception {
        ModelScanRevision revisions = new ModelScanRevision();
        AtomicReference<Map<String, String>> catalog = new AtomicReference<>(Map.of("avatar", "previous"));
        CountDownLatch oldFinalizeReady = new CountDownLatch(1);
        CountDownLatch allowOldFinalize = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        long oldRevision = revisions.begin();
        try {
            Future<Boolean> oldFinalize = worker.submit(() -> {
                Map<String, String> oldCandidate = Map.of();
                oldFinalizeReady.countDown();
                assertTrue(allowOldFinalize.await(5, TimeUnit.SECONDS));
                return revisions.runIfCurrent(oldRevision, () -> catalog.set(oldCandidate));
            });

            assertTrue(oldFinalizeReady.await(5, TimeUnit.SECONDS));
            long newRevision = revisions.begin();
            Map<String, String> newCandidate = Map.of("avatar", "newer");
            assertTrue(revisions.runIfCurrent(newRevision, () -> catalog.set(newCandidate)));
            allowOldFinalize.countDown();

            assertFalse(oldFinalize.get(5, TimeUnit.SECONDS));
            assertEquals(Map.of("avatar", "newer"), catalog.get());
        } finally {
            allowOldFinalize.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void everyManagerAppliesCatalogOnlyInsideTheAtomicRevisionGate() throws IOException {
        Path managerPath = firstExisting(
                Path.of("fabric/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("src/neoforge/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("common/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"));
        String source = Files.readString(managerPath, StandardCharsets.UTF_8);
        int gate = source.indexOf("boolean finalized = LOCAL_MODEL_SCAN_REVISION.runIfCurrent(scanRevision, () -> {");
        int apply = source.indexOf("localModelSourcePaths.clear();", gate);
        int staleSettlement = source.indexOf("if (!finalized)", apply);

        assertTrue(gate >= 0, "manager scan finalization must use the atomic revision gate: " + managerPath);
        assertTrue(apply > gate, "catalog mutation must occur after entering the gate: " + managerPath);
        assertTrue(staleSettlement > apply, "superseded scans must settle after the gated apply: " + managerPath);
    }

    private static Path firstExisting(Path... candidates) throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            for (Path candidate : candidates) {
                Path resolved = directory.resolve(candidate);
                if (Files.isRegularFile(resolved)) return resolved;
            }
            directory = directory.getParent();
        }
        throw new IOException("ClientModelManager source not found");
    }
}
