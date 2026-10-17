package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CloudConcurrencyQueueTest {
    @Test void unlimitedStartsEveryRequestWithoutWaiting() {
        var queue = new CloudConcurrencyQueue(); var started = new AtomicInteger();
        for (int i = 0; i < 100; i++) queue.submit(() -> { started.incrementAndGet(); return new CompletableFuture<>(); });
        assertEquals(100, started.get());
    }
    @Test void limitIsHeldUntilUnderlyingWorkFinishesEvenWhenCallerCancels() {
        var queue = new CloudConcurrencyQueue(); queue.limit(1);
        var first = new CompletableFuture<Integer>(); var started = new AtomicInteger();
        var caller = queue.submit(() -> first);
        var second = queue.submit(() -> { started.incrementAndGet(); return CompletableFuture.completedFuture(2); });
        caller.cancel(false); assertEquals(0, started.get());
        first.complete(1); assertEquals(2, second.join());
    }
    @Test void decreasingLimitDoesNotInterruptActiveWorkAndUnlimitedDrainsWaiting() {
        var queue = new CloudConcurrencyQueue(); queue.limit(2);
        var one = new CompletableFuture<Integer>(); var two = new CompletableFuture<Integer>();
        queue.submit(() -> one); queue.submit(() -> two); queue.limit(1);
        var three = queue.submit(() -> CompletableFuture.completedFuture(3));
        one.complete(1); assertFalse(three.isDone()); assertFalse(two.isDone());
        queue.limit(0); assertEquals(3, three.join()); assertFalse(two.isDone());
    }
    @Test void failuresAndCancelledQueuedJobsReleaseCapacity() {
        var queue = new CloudConcurrencyQueue(); queue.limit(1);
        var work = new CompletableFuture<Void>(); queue.submit(() -> work);
        var cancelled = queue.submit(() -> { fail("Cancelled queued work ran"); return CompletableFuture.completedFuture(0); });
        cancelled.cancel(false);
        var failure = queue.submit(() -> { throw new IllegalStateException("expected"); });
        var next = queue.submit(() -> CompletableFuture.completedFuture(42));
        work.complete(null); assertThrows(java.util.concurrent.CompletionException.class, failure::join);
        assertEquals(42, next.join());
    }
    @Test void parallelAdmissionsRespectCap() {
        var queue = new CloudConcurrencyQueue(); queue.limit(4);
        var active = new AtomicInteger(); var peak = new AtomicInteger();
        List<CompletableFuture<Integer>> results = java.util.stream.IntStream.range(0, 120).parallel().mapToObj(i ->
            queue.submit(() -> CompletableFuture.supplyAsync(() -> {
                int count = active.incrementAndGet(); peak.accumulateAndGet(count, Math::max);
                try { Thread.sleep(2); return i; } catch (InterruptedException e) { throw new RuntimeException(e); }
                finally { active.decrementAndGet(); }
            }))).toList();
        assertEquals(7140, results.stream().mapToInt(CompletableFuture::join).sum());
        assertTrue(peak.get() <= 4); assertEquals(0, active.get());
    }
    @Test void presetsKeepUploadAndProcessingBudgetsIndependent() {
        assertEquals(0, CloudClientPerformance.Preset.UNLIMITED.uploads(12));
        assertEquals(0, CloudClientPerformance.Preset.UNLIMITED.processing(12));
        assertEquals(2, CloudClientPerformance.Preset.LOW.uploads(0));
        assertEquals(1, CloudClientPerformance.Preset.LOW.processing(0));
        assertEquals(4, CloudClientPerformance.Preset.MEDIUM.uploads(0));
        assertEquals(2, CloudClientPerformance.Preset.MEDIUM.processing(0));
        assertEquals(8, CloudClientPerformance.Preset.HIGH.uploads(0));
        assertEquals(4, CloudClientPerformance.Preset.HIGH.processing(0));
        assertEquals(7, CloudClientPerformance.Preset.CUSTOM.uploads(7));
        assertEquals(0, CloudClientPerformance.Preset.CUSTOM.processing(0));
    }
}
