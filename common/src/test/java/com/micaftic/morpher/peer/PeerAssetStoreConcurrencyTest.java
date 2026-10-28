package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class PeerAssetStoreConcurrencyTest {
    @TempDir Path temporary;

    @Test void activeCleanupCannotBreakAnotherTransferAdmission() throws Exception {
        try (var store = new PeerAssetStore(temporary);
             var pool = Executors.newSingleThreadExecutor()) {
            Path staging = temporary.resolve("staging");
            Path active = staging.resolve("partial-active");
            try (var reservation = store.reserveTransient(active, 4096, true)) {
                Files.createDirectory(active);
                var start = new CountDownLatch(1);
                var cleanup = pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 1000; i++) {
                        Path changing = Files.createDirectory(active.resolve("changing-" + i));
                        Files.write(changing.resolve("bytes"), new byte[32]);
                        PeerSafeFiles.deletePrivateTree(active, changing);
                    }
                    return null;
                });
                start.countDown();
                int admissions = 0;
                do {
                    try (var other = store.reserveTransient(staging.resolve("other"), 1, true)) {
                        admissions++;
                    }
                } while (!cleanup.isDone() || admissions < 100);
                cleanup.get(20, TimeUnit.SECONDS);
                assertTrue(admissions >= 100);
            }
        }
    }

    @Test void skippingActiveTreesStillChargesReservedAndUnreservedBytes() throws Exception {
        try (var store = new PeerAssetStore(temporary, 128)) {
            Path active = temporary.resolve("staging/partial-active");
            try (var reservation = store.reserveTransient(active, 64, true)) {
                Files.createDirectory(active);
                Files.write(active.resolve("bytes"), new byte[64]);
                try (var remaining = store.reserveTransient(temporary.resolve("staging/fits"), 64, true)) {
                    assertThrows(PeerFailure.class, () -> store.reserveTransient(
                            temporary.resolve("staging/over-budget"), 1, true));
                }
            }
            // Once the reservation ends, its surviving bytes must be counted on disk.
            assertThrows(PeerFailure.class, () -> store.reserveTransient(
                    temporary.resolve("staging/over-budget"), 65, true));
        }
    }
}
