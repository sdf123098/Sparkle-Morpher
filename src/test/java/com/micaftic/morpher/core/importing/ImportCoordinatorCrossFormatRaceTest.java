package com.micaftic.morpher.core.importing;

import com.micaftic.morpher.core.storage.ImportCommitFlow;
import com.micaftic.morpher.core.storage.LocalModelImportStore;
import com.micaftic.morpher.resource.gltf.GltfLoadResult;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportCoordinatorCrossFormatRaceTest {
    @TempDir
    Path tempDir;

    @Test
    void newerGltfImportSupersedesSlowLegacyImportForSameModelKey() throws Exception {
        Path root = tempDir.resolve("cross-format-race");
        LocalModelImportStore store = new LocalModelImportStore(root);
        Path oldSource = store.persist("avatar", "avatar.ysm", "old-source".getBytes(StandardCharsets.UTF_8));
        AtomicReference<String> published = new AtomicReference<>("old-assembly");
        var requests = new com.micaftic.morpher.core.model.lifecycle.KeyedRequestLeaseRegistry<String>();
        var slowLease = requests.begin("avatar");
        CountDownLatch slowParserStarted = new CountDownLatch(1);
        CountDownLatch allowSlowParserToFinish = new CountDownLatch(1);
        AtomicInteger releasedCandidates = new AtomicInteger();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        byte[] legacyBytes = "legacy-A".getBytes(StandardCharsets.UTF_8);
        byte[] gltfBytes = "{\"asset\":{\"version\":\"2.0\",\"generator\":\"gltf-B\"}}"
                .getBytes(StandardCharsets.UTF_8);

        try {
            Future<ImportCommitFlow.Outcome<LocalModelImportStore.CommitResult>> slowImport = worker.submit(() -> {
                try (LocalModelImportStore.PreparedImport prepared = store.prepare("avatar", "avatar.bbmodel", legacyBytes)) {
                    return ImportCoordinator.importPrepared(prepared,
                            () -> {
                                slowParserStarted.countDown();
                                assertTrue(allowSlowParserToFinish.await(5, TimeUnit.SECONDS));
                                return ImportCoordinator.parsePickedBytes("avatar.bbmodel", legacyBytes, RawYsmModel::new);
                            },
                            raw -> "legacy-A",
                            result -> { throw new AssertionError("legacy import must not use the glTF builder"); },
                            candidate -> ImportCoordinator.commitBuiltCandidate(candidate,
                                    () -> requests.isCurrent(slowLease), prepared::commit,
                                    (built, committed) -> published.set(built), ignored -> releasedCandidates.incrementAndGet()));
                } finally {
                    requests.complete(slowLease);
                }
            });

            assertTrue(slowParserStarted.await(5, TimeUnit.SECONDS));
            assertTrue(requests.invalidate("avatar"));
            var fastLease = requests.begin("avatar");
            assertTrue(requests.isCurrent(fastLease));
            try (LocalModelImportStore.PreparedImport prepared = store.prepare("avatar", "avatar.gltf", gltfBytes)) {
                var fastOutcome = ImportCoordinator.importPrepared(prepared,
                        () -> ImportCoordinator.parsePickedBytes("avatar.gltf", gltfBytes, RawYsmModel::new),
                        raw -> { throw new AssertionError("glTF import must not use the legacy builder"); },
                        (GltfLoadResult result) -> "gltf-B",
                        candidate -> ImportCoordinator.commitBuiltCandidate(candidate,
                                () -> requests.isCurrent(fastLease), prepared::commit,
                                (built, committed) -> published.set(built), ignored -> releasedCandidates.incrementAndGet()));
                assertEquals(ImportCommitFlow.State.PUBLISHED, fastOutcome.state());
            }
            assertTrue(requests.complete(fastLease));

            var nextLease = requests.begin("avatar");
            allowSlowParserToFinish.countDown();
            ImportCommitFlow.Outcome<LocalModelImportStore.CommitResult> slowOutcome = slowImport.get(5, TimeUnit.SECONDS);

            assertEquals(ImportCommitFlow.State.SUPERSEDED_BEFORE_COMMIT, slowOutcome.state());
            assertEquals("gltf-B", published.get());
            assertTrue(requests.isCurrent(nextLease), "the old request's finally block must not clear the next lease");
            assertEquals(1, releasedCandidates.get(), "the stale legacy candidate must be released exactly once");
            assertFalse(Files.exists(oldSource));
            assertTrue(Files.exists(root.resolve("avatar.gltf")));
            assertFalse(Files.exists(root.resolve("avatar.bbmodel")));
            assertTrue(requests.complete(nextLease));
        } finally {
            allowSlowParserToFinish.countDown();
            worker.shutdownNow();
        }
    }
}
