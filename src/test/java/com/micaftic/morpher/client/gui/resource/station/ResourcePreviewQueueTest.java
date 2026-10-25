package com.micaftic.morpher.client.gui.resource.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePreviewQueueTest {
    private record Request(String id, String label) {}
    private record Call(Request request, DownloadCancellation cancellation, CompletableFuture<Void> future) {}

    private static final class Fixture {
        final List<Call> calls = new ArrayList<>();
        final ResourcePreviewQueue<Request> queue = new ResourcePreviewQueue<>(Request::id, (request, cancellation) -> {
            var future = new CompletableFuture<Void>();
            calls.add(new Call(request, cancellation, future));
            return future;
        });
        void update(String... ids) {
            queue.update(java.util.Arrays.stream(ids).map(id -> new Request(id, id)).toList());
        }
        List<String> started() { return calls.stream().map(call -> call.request().id()).toList(); }
    }

    @Test void startsOnlyOneRequestAndDeduplicatesByStableIdentity() {
        var f = new Fixture();
        f.queue.update(List.of(new Request("a", "old"), new Request("a", "duplicate"), new Request("b", "B")));
        assertEquals(List.of("a"), f.started());
        f.queue.update(List.of(new Request("a", "renamed"), new Request("b", "B")));
        assertFalse(f.calls.getFirst().cancellation().isCancelled());
        f.calls.getFirst().future().complete(null);
        assertEquals(List.of("a", "b"), f.started());
        f.calls.get(1).future().complete(null);
        f.update("a", "b");
        assertEquals(List.of("a", "b"), f.started());
    }

    @Test void pagingCancelsTheOldReadAndWaitsForItsWorkerBeforeStartingTheNewPage() {
        var f = new Fixture();
        f.update("a", "stale-pending");
        var old = f.calls.getFirst();
        f.update("c", "d");
        assertTrue(old.cancellation().isCancelled());
        assertFalse(old.future().isDone(), "Token cancellation must not pretend the worker has exited");
        assertEquals(List.of("a"), f.started());
        old.future().completeExceptionally(new CancellationException());
        assertEquals(List.of("a", "c"), f.started());
        assertFalse(f.calls.get(1).cancellation().isCancelled());
        f.calls.get(1).future().complete(null);
        assertEquals(List.of("a", "c", "d"), f.started());
    }

    @Test void returningBeforeAnOldCancelledAttemptFinishesGetsAFreshToken() {
        var f = new Fixture();
        f.update("a");
        var old = f.calls.getFirst();
        f.update("b");
        f.update("a");
        assertTrue(old.cancellation().isCancelled());
        assertEquals(List.of("a"), f.started());
        old.future().completeExceptionally(new IllegalStateException("stale failure"));
        assertEquals(List.of("a", "a"), f.started());
        assertNotSame(old.cancellation(), f.calls.get(1).cancellation());
        assertFalse(f.calls.get(1).cancellation().isCancelled());
    }

    @Test void pausingCancelsActiveWorkAndDiscardsPendingWithoutClosingTheQueue() {
        var f = new Fixture();
        f.update("a", "b");
        f.queue.pause();
        assertTrue(f.calls.getFirst().cancellation().isCancelled());
        f.calls.getFirst().future().complete(null);
        assertEquals(List.of("a"), f.started());
        f.update("c");
        assertEquals(List.of("a", "c"), f.started());
    }

    @Test void closingCancelsActiveWorkAndNeverStartsAnythingElse() {
        var f = new Fixture();
        f.update("a", "b");
        f.queue.close();
        f.queue.close();
        assertTrue(f.calls.getFirst().cancellation().isCancelled());
        f.calls.getFirst().future().completeExceptionally(new CancellationException());
        f.update("c");
        assertEquals(List.of("a"), f.started());
    }

    @Test void failuresDoNotRetryEveryFrameOrBlockLaterVisibleCards() {
        var f = new Fixture();
        f.update("a", "b");
        f.calls.getFirst().future().completeExceptionally(new IllegalArgumentException("bad image"));
        assertEquals(List.of("a", "b"), f.started());
        f.calls.get(1).future().complete(null);
        for (int frame = 0; frame < 10; frame++) f.update("a", "b");
        assertEquals(List.of("a", "b"), f.started());
        f.queue.pause();
        f.update("a");
        assertEquals(List.of("a", "b", "a"), f.started());
    }

    @Test void synchronousCompletionDrainsWithoutRecursiveStackGrowth() {
        AtomicInteger loaded = new AtomicInteger();
        var queue = new ResourcePreviewQueue<Integer>(Object::toString, (request, cancellation) -> {
            loaded.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        });
        queue.update(java.util.stream.IntStream.range(0, 10_000).boxed().toList());
        assertEquals(10_000, loaded.get());
    }

    @Test void cancellationCallbacksMayFinishTheOldWorkerInline() {
        var calls = new ArrayList<String>();
        var queue = new ResourcePreviewQueue<String>(id -> id, (request, cancellation) -> {
            calls.add(request);
            var future = new CompletableFuture<Void>();
            cancellation.onCancel(() -> future.completeExceptionally(new CancellationException()));
            return future;
        });
        queue.update(List.of("a", "stale"));
        queue.update(List.of("c"));
        assertEquals(List.of("a", "c"), calls);
        queue.close();
        assertEquals(List.of("a", "c"), calls);
    }

    @Test void aLoaderCancelledByItsOwnBudgetDoesNotRetryItselfForever() {
        AtomicInteger loaded = new AtomicInteger();
        var queue = new ResourcePreviewQueue<String>(id -> id, (request, cancellation) -> {
            loaded.incrementAndGet();
            cancellation.cancel();
            return CompletableFuture.failedFuture(new CancellationException("preview budget"));
        });
        queue.update(List.of("a", "b"));
        queue.update(List.of("a", "b"));
        assertEquals(2, loaded.get());
    }
}
