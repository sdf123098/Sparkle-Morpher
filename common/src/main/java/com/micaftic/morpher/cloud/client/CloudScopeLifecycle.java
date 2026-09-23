package com.micaftic.morpher.cloud.client;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the short-lived Cloud state for one selected Minecraft scope/world.
 *
 * <p>The loader only calls {@link #enter(String, String)} and {@link #leave()}.
 * Recovery, heartbeats and reconnect attempts stay outside the Minecraft
 * thread and are invalidated by a monotonically increasing generation.</p>
 */
public final class CloudScopeLifecycle implements AutoCloseable {
    static final int RECOVERY_PAGE_SIZE = 256;
    static final long HEARTBEAT_PERIOD_SECONDS = 15L;

    private final CloudScopeClient scopes;
    private final CloudRealtimeClient realtime;
    private final CloudAppearanceStore appearances;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;
    private final AtomicLong generations = new AtomicLong();

    private volatile ScopeContext active;
    private volatile long recoveryCursor;
    private volatile ScheduledFuture<?> heartbeatTask;
    private volatile ScheduledFuture<?> reconnectTask;

    public CloudScopeLifecycle(
            CloudScopeClient scopes,
            CloudRealtimeClient realtime,
            CloudAppearanceStore appearances
    ) {
        this(scopes, realtime, appearances, Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "spm-cloud-scope");
            thread.setDaemon(true);
            return thread;
        }), true);
    }

    CloudScopeLifecycle(
            CloudScopeClient scopes,
            CloudRealtimeClient realtime,
            CloudAppearanceStore appearances,
            ScheduledExecutorService scheduler
    ) {
        this(scopes, realtime, appearances, scheduler, false);
    }

    private CloudScopeLifecycle(
            CloudScopeClient scopes,
            CloudRealtimeClient realtime,
            CloudAppearanceStore appearances,
            ScheduledExecutorService scheduler,
            boolean ownsScheduler
    ) {
        this.scopes = Objects.requireNonNull(scopes, "scopes");
        this.realtime = Objects.requireNonNull(realtime, "realtime");
        this.appearances = Objects.requireNonNull(appearances, "appearances");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.ownsScheduler = ownsScheduler;
    }

    public synchronized CompletableFuture<Void> enter(String scopeId, String worldEpoch) {
        requireText(scopeId, "scopeId");
        requireText(worldEpoch, "worldEpoch");
        leaveLocked();
        ScopeContext next = new ScopeContext(scopeId, worldEpoch, generations.incrementAndGet());
        active = next;
        recoveryCursor = 0L;
        return connectAndRecover(next, 0);
    }

    public synchronized void leave() {
        leaveLocked();
    }

    public String activeScopeId() {
        ScopeContext current = active;
        return current == null ? null : current.scopeId();
    }

    public String activeWorldEpoch() {
        ScopeContext current = active;
        return current == null ? null : current.worldEpoch();
    }

    public long recoveryCursor() {
        return recoveryCursor;
    }

    private CompletableFuture<Void> connectAndRecover(ScopeContext context, int attempt) {
        if (!isCurrent(context)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Cloud scope is no longer active"));
        }
        CompletableFuture<Void> result = realtime.connect()
                .thenCompose(ignored -> realtime.joinScope(context.scopeId(), context.worldEpoch()))
                .thenCompose(ignored -> recoverAll(context));
        result.whenComplete((ignored, failure) -> {
            if (failure == null) {
                scheduleHeartbeat(context);
            } else if (isCurrent(context)) {
                scheduleReconnect(context, Math.min(attempt + 1, 6));
            }
        });
        return result;
    }

    private CompletableFuture<Void> recoverAll(ScopeContext context) {
        if (!isCurrent(context)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Cloud scope is no longer active"));
        }
        long after = recoveryCursor;
        return scopes.recoverEvents(context.scopeId(), after, RECOVERY_PAGE_SIZE).thenCompose(recovery -> {
            if (!isCurrent(context)) {
                return CompletableFuture.failedFuture(new IllegalStateException("Cloud scope is no longer active"));
            }
            appearances.applyRecovery(context.scopeId(), recovery);
            long next = after;
            for (CloudScopeClient.CloudRecoveredEvent event : recovery.events()) {
                if (event.sequence() < next) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Cloud recovery sequence moved backwards"));
                }
                next = Math.max(next, event.sequence());
            }
            next = Math.max(next, recovery.toCursor());
            if (recovery.hasMore()) {
                if (next <= after) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Cloud recovery made no progress"));
                }
                recoveryCursor = next;
                return recoverAll(context);
            }
            recoveryCursor = next;
            return CompletableFuture.completedFuture(null);
        });
    }

    private void scheduleHeartbeat(ScopeContext context) {
        synchronized (this) {
            if (!isCurrent(context)) return;
            cancel(heartbeatTask);
            heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
                if (!isCurrent(context)) return;
                realtime.heartbeat(System.currentTimeMillis()).whenComplete((ignored, failure) -> {
                    if (failure != null && isCurrent(context)) {
                        scheduleReconnect(context, 1);
                    }
                });
            }, HEARTBEAT_PERIOD_SECONDS, HEARTBEAT_PERIOD_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void scheduleReconnect(ScopeContext context, int attempt) {
        synchronized (this) {
            if (!isCurrent(context) || (reconnectTask != null && !reconnectTask.isDone())) return;
            long baseSeconds = 1L << Math.min(attempt, 6);
            long delaySeconds = Math.min(60L, baseSeconds) + ThreadLocalRandom.current().nextLong(0, 2);
            reconnectTask = scheduler.schedule(() -> {
                reconnectTask = null;
                connectAndRecover(context, attempt).exceptionally(ignored -> null);
            }, delaySeconds, TimeUnit.SECONDS);
        }
    }

    private synchronized void leaveLocked() {
        generations.incrementAndGet();
        ScopeContext previous = active;
        active = null;
        recoveryCursor = 0L;
        cancel(heartbeatTask);
        cancel(reconnectTask);
        heartbeatTask = null;
        reconnectTask = null;
        if (previous != null) {
            realtime.leaveScope(previous.scopeId());
            appearances.clearScope(previous.scopeId());
        }
    }

    private boolean isCurrent(ScopeContext context) {
        ScopeContext current = active;
        return current == context && generations.get() == context.generation();
    }

    private static void cancel(ScheduledFuture<?> task) {
        if (task != null) task.cancel(false);
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

    private record ScopeContext(String scopeId, String worldEpoch, long generation) {}
}
