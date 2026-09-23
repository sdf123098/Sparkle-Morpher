package com.micaftic.morpher.cloud.client;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Coordinates safe materialization of one trusted Cloud asset revision.
 *
 * <p>The catalog is authoritative for the complete {@link CloudAssetRef}; a
 * caller cannot download an unlisted revision. Requests for the same exact
 * revision share one asynchronous download, while a failed request is removed
 * so a later appearance update can retry it. The supplied downloader is
 * expected to perform network and cache I/O off the client thread.</p>
 */
public final class CloudAssetMaterializationCoordinator {
    private final CloudAssetCatalogStore catalog;
    private final Function<CloudAssetRef, CompletableFuture<Path>> downloader;
    private final ConcurrentMap<CloudAssetRef, CompletableFuture<Path>> inFlight = new ConcurrentHashMap<>();

    public CloudAssetMaterializationCoordinator(
            CloudAssetCatalogStore catalog,
            Function<CloudAssetRef, CompletableFuture<Path>> downloader
    ) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.downloader = Objects.requireNonNull(downloader, "downloader");
    }

    public CompletableFuture<Path> ensure(CloudAssetRef ref) {
        Objects.requireNonNull(ref, "ref");
        CloudAssetSummary summary = catalog.get(ref.assetId());
        if (summary == null || !ref.equals(summary.ref())) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Cloud asset revision is not in the trusted catalog"));
        }

        synchronized (this) {
            CompletableFuture<Path> existing = inFlight.get(ref);
            if (existing != null) return existing;
            CompletableFuture<Path> created;
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

    public int inFlightCount() {
        return inFlight.size();
    }
}
