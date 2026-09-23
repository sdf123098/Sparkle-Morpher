package com.micaftic.morpher.cloud.client;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coalesces observation reports for loader-specific player/fake/maid adapters.
 *
 * <p>An entity is never registered by observing it. Only a binding already
 * accepted by {@link CloudEntityBindingResolver} may reach the Cloud API. The
 * payload contains no position, inventory, ownership or arbitrary entity
 * data.</p>
 */
public final class CloudEntityObservationCoordinator implements AutoCloseable {
    private static final long FLUSH_DELAY_MILLIS = 100L;

    private final CloudScopeClient scopes;
    private final CloudEntityBindingResolver resolver;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;
    private final AtomicLong generations = new AtomicLong();
    private final ConcurrentMap<String, Pending> pending = new ConcurrentHashMap<>();

    private volatile Context context = new Context("", "", 0L);
    private volatile ScheduledFuture<?> flushTask;

    public CloudEntityObservationCoordinator(
            CloudScopeClient scopes,
            CloudEntityBindingResolver resolver
    ) {
        this(scopes, resolver, Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "spm-cloud-observation");
            thread.setDaemon(true);
            return thread;
        }), true);
    }

    CloudEntityObservationCoordinator(
            CloudScopeClient scopes,
            CloudEntityBindingResolver resolver,
            ScheduledExecutorService scheduler
    ) {
        this(scopes, resolver, scheduler, false);
    }

    private CloudEntityObservationCoordinator(
            CloudScopeClient scopes,
            CloudEntityBindingResolver resolver,
            ScheduledExecutorService scheduler,
            boolean ownsScheduler
    ) {
        this.scopes = Objects.requireNonNull(scopes, "scopes");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.ownsScheduler = ownsScheduler;
    }

    public synchronized void enter(String scopeId, String worldEpoch) {
        requireText(scopeId, "scopeId");
        requireText(worldEpoch, "worldEpoch");
        leaveLocked();
        context = new Context(scopeId, worldEpoch, generations.incrementAndGet());
    }

    public synchronized void leave() {
        leaveLocked();
    }

    public CompletableFuture<ObservationResult> report(
            UUID entityUuid,
            String entityKind,
            ObservationState state
    ) {
        Objects.requireNonNull(entityUuid, "entityUuid");
        requireText(entityKind, "entityKind");
        Objects.requireNonNull(state, "state");
        Context current = context;
        CloudEntityBindingResolver.Resolution resolution = resolver.resolve(
                current.scopeId(), current.worldEpoch(), entityUuid, entityKind);
        if (resolution.status() != CloudEntityBindingResolver.Status.BOUND) {
            return CompletableFuture.completedFuture(new ObservationResult(resolution.status(), resolution.binding(), false));
        }
        CompletableFuture<ObservationResult> result = new CompletableFuture<>();
        pending.compute(resolution.binding().bindingId(), (ignored, previous) -> {
            Pending next = previous == null ? new Pending(current, state) : previous;
            next.state = state;
            next.waiters.add(result);
            return next;
        });
        scheduleFlush();
        return result;
    }

    public String activeScopeId() {
        return context.scopeId();
    }

    public String activeWorldEpoch() {
        return context.worldEpoch();
    }

    private synchronized void scheduleFlush() {
        if (flushTask != null && !flushTask.isDone()) return;
        flushTask = scheduler.schedule(this::flush, FLUSH_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void flush() {
        for (var entry : pending.entrySet()) {
            if (!pending.remove(entry.getKey(), entry.getValue())) continue;
            Pending item = entry.getValue();
            if (!isCurrent(item.context)) {
                complete(item.waiters, new ObservationResult(CloudEntityBindingResolver.Status.STALE_CONTEXT, null, false));
                continue;
            }
            scopes.observeBinding(
                    entry.getKey(),
                    new CloudScopeClient.CloudBindingObservation(item.context.worldEpoch(), item.state.wireValue()))
                    .thenAccept(binding -> complete(item.waiters,
                            new ObservationResult(CloudEntityBindingResolver.Status.BOUND, binding, true)))
                    .whenComplete((ignored, failure) -> {
                        if (failure != null) item.waiters.forEach(waiter -> waiter.completeExceptionally(failure));
                    });
        }
        synchronized (this) {
            flushTask = null;
            if (!pending.isEmpty()) scheduleFlush();
        }
    }

    private synchronized void leaveLocked() {
        generations.incrementAndGet();
        context = new Context("", "", generations.get());
        if (flushTask != null) flushTask.cancel(false);
        flushTask = null;
        pending.forEach((ignored, item) -> complete(item.waiters,
                new ObservationResult(CloudEntityBindingResolver.Status.STALE_CONTEXT, null, false)));
        pending.clear();
    }

    private boolean isCurrent(Context candidate) {
        Context current = context;
        return current == candidate && generations.get() == candidate.generation();
    }

    private static void complete(List<CompletableFuture<ObservationResult>> waiters, ObservationResult result) {
        waiters.forEach(waiter -> waiter.complete(result));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    @Override
    public void close() {
        leave();
        if (ownsScheduler) scheduler.shutdownNow();
    }

    public enum ObservationState {
        VISIBLE,
        NOT_VISIBLE,
        UNKNOWN;

        String wireValue() {
            return name();
        }
    }

    public record ObservationResult(
            CloudEntityBindingResolver.Status status,
            CloudScopeClient.CloudEntityBinding binding,
            boolean submitted
    ) {}

    private static final class Pending {
        private final Context context;
        private final List<CompletableFuture<ObservationResult>> waiters = new CopyOnWriteArrayList<>();
        private ObservationState state;

        private Pending(Context context, ObservationState state) {
            this.context = context;
            this.state = state;
        }
    }

    private record Context(String scopeId, String worldEpoch, long generation) {}
}
