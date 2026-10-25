package com.micaftic.morpher.cloud.client;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Coordinates safe materialization of one trusted Cloud asset revision.
 *
 * <p>The catalog is authoritative for the complete {@link CloudAssetRef}; a
 * caller cannot download an unlisted revision. Requests for the same exact
 * revision share one asynchronous download, while a failed request is removed
 * so a later appearance update can retry it. The supplied downloader is
 * expected to perform network I/O off the client thread and returns verified
 * bytes only; display materialization never writes a file.</p>
 */
public final class CloudAssetMaterializationCoordinator implements AutoCloseable {
    private volatile boolean closed;
    private final CloudAssetCatalogStore catalog;
    private final Function<CloudAssetRef, CompletableFuture<CloudAssetClient.MemoryAsset>> downloader;
    private final Predicate<CloudAssetRef> previouslyApplied;
    private final ConcurrentMap<CloudAssetRef, CompletableFuture<CloudAssetClient.MemoryAsset>> inFlight = new ConcurrentHashMap<>();

    public CloudAssetMaterializationCoordinator(
            CloudAssetCatalogStore catalog,
            Function<CloudAssetRef, CompletableFuture<CloudAssetClient.MemoryAsset>> downloader
    ) {
        this(catalog, downloader, ref -> false);
    }

    public CloudAssetMaterializationCoordinator(CloudAssetCatalogStore catalog,
            Function<CloudAssetRef, CompletableFuture<CloudAssetClient.MemoryAsset>> downloader, Predicate<CloudAssetRef> previouslyApplied) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.downloader = Objects.requireNonNull(downloader, "downloader");
        this.previouslyApplied = Objects.requireNonNull(previouslyApplied, "previouslyApplied");
    }

    public CompletableFuture<CloudAssetClient.MemoryAsset> ensure(CloudAssetRef ref) {
        Objects.requireNonNull(ref, "ref");
        if (closed) return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException("Cloud runtime closed"));
        CloudAssetSummary summary = catalog.get(ref.assetId());
        if (summary == null || !ref.equals(summary.ref())) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Cloud asset revision is not in the trusted catalog"));
        }

        return materialize(ref);
    }

    /** Exact saved application only. The downloader still checks current server ACL and the SHA. */
    public CompletableFuture<CloudAssetClient.MemoryAsset> ensurePreviouslyApplied(CloudAssetRef ref) {
        Objects.requireNonNull(ref, "ref");
        if (closed) return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException("Cloud runtime closed"));
        try {
            if (!previouslyApplied.test(ref)) return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Cloud asset revision has not previously been applied"));
        } catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
        return materialize(ref);
    }

    private CompletableFuture<CloudAssetClient.MemoryAsset> materialize(CloudAssetRef ref) {
        synchronized (this) {
            if (closed) return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException("Cloud runtime closed"));
            CompletableFuture<CloudAssetClient.MemoryAsset> existing = inFlight.get(ref);
            if (existing != null) return existing;
            CompletableFuture<CloudAssetClient.MemoryAsset> created;
            try {
                created = Objects.requireNonNull(downloader.apply(ref), "downloader result");
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
            inFlight.put(ref, created);
            created.whenComplete((ignored, failure) -> inFlight.remove(ref, created));
            return created;
        }
    }

    /** Cancels the downloader future, not just a dependent consumer stage. */
    @Override
    public synchronized void close() {
        closed = true;
        cancelPending();
    }

    public synchronized void cancelPending() {
        var pending = java.util.List.copyOf(inFlight.values());
        inFlight.clear();
        pending.forEach(future -> future.cancel(true));
    }

    public boolean isClosed() { return closed; }

    public int inFlightCount() {
        return inFlight.size();
    }
}
