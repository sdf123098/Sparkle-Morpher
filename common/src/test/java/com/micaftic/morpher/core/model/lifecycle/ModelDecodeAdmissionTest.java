package com.micaftic.morpher.core.model.lifecycle;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ModelDecodeAdmissionTest {
    @Test void decodesNeverOverlapAndFailureReleasesAdmission() throws Exception {
        var gate = new ModelDecodeAdmission();
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var jobs = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 8; i++) jobs.add(executor.submit(() -> {
                try { start.await(); } catch (InterruptedException error) { throw new RuntimeException(error); }
                gate.run(() -> {
                    int count = active.incrementAndGet();
                    maximum.accumulateAndGet(count, Math::max);
                    try { Thread.sleep(5); } catch (InterruptedException error) { throw new RuntimeException(error); }
                    finally { active.decrementAndGet(); }
                });
            }));
            start.countDown();
            for (var job : jobs) job.get(5, TimeUnit.SECONDS);
        }
        assertEquals(1, maximum.get());
        assertThrows(IllegalStateException.class, () -> gate.run(() -> { throw new IllegalStateException(); }));
        gate.run(() -> assertEquals(0, active.get()));
    }

    @Test void previewAdmissionLeavesHeapForWorldAndRejectsExpandedTexturePeak() {
        long mib = 1024L * 1024;
        assertTrue(ModelDecodeAdmission.previewFits(64 * mib, 1536 * mib, 1000 * mib));
        assertFalse(ModelDecodeAdmission.previewFits(64 * mib, 1536 * mib, 400 * mib));
        assertFalse(ModelDecodeAdmission.previewFits(Long.MAX_VALUE, 1536 * mib, 1536 * mib));
    }

    @Test void textureAdmissionUsesDecodedDimensionsAndCheckedArithmetic() {
        long mib = 1024L * 1024;
        assertTrue(ModelDecodeAdmission.textureFits(4096, 4096, 2, 1536 * mib, 400 * mib));
        assertFalse(ModelDecodeAdmission.textureFits(8192, 8192, 2, 1536 * mib, 400 * mib));
        assertFalse(ModelDecodeAdmission.textureFits(Integer.MAX_VALUE, Integer.MAX_VALUE, 2, Long.MAX_VALUE, Long.MAX_VALUE));
        assertFalse(ModelDecodeAdmission.textureFits(65536, 65536, 1, Long.MAX_VALUE, Long.MAX_VALUE));
        assertFalse(ModelDecodeAdmission.textureFits(-1, 64, 2, 1536 * mib, 1000 * mib));
        assertFalse(ModelDecodeAdmission.textureFits(64, 64, 0, 1536 * mib, 1000 * mib));
    }
}
