package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CloudEntityPublicationGuardTest {
    @Test void invalidatedSessionPreventsQueuedPublication() {
        var tasks = new ArrayList<Runnable>(); var active = new AtomicBoolean(true); var writes = new AtomicInteger();
        var result = CloudEntityPublicationGuard.run(tasks::add, active::get, () -> {
            writes.incrementAndGet(); return CompletableFuture.completedFuture(1);
        });
        active.set(false); tasks.remove(0).run();
        assertTrue(result.isCompletedExceptionally()); assertEquals(0, writes.get());
    }
    @Test void privacyEnabledBetweenRevisionAndPublishStopsWrite() {
        var active = new AtomicBoolean(true); var revision = new CompletableFuture<Long>(); var writes = new AtomicInteger();
        var result = CloudEntityPublicationGuard.run(Runnable::run, active::get, () -> revision)
                .thenCompose(value -> CloudEntityPublicationGuard.run(Runnable::run, active::get, () -> {
                    writes.incrementAndGet(); return CompletableFuture.completedFuture(value + 1);
                }));
        active.set(false); revision.complete(3L);
        assertTrue(result.isCompletedExceptionally()); assertEquals(0, writes.get());
    }
    @Test void currentSessionPublishesAndPreservesServerFailure() {
        assertEquals(4, CloudEntityPublicationGuard.run(Runnable::run, () -> true,
                () -> CompletableFuture.completedFuture(4)).join());
        var denied = CloudEntityPublicationGuard.run(Runnable::run, () -> true,
                () -> CompletableFuture.failedFuture(new IllegalStateException("403")));
        assertTrue(denied.isCompletedExceptionally());
    }
}
