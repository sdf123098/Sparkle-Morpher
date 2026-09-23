package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudAssetMaterializationCoordinatorTest {
    private static final CloudAssetRef REF = new CloudAssetRef(
            "cloud-model", 3L, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

    @Test
    void deduplicatesSameRevisionAndAllowsRetryAfterFailure() {
        CloudAssetCatalogStore catalog = new CloudAssetCatalogStore();
        catalog.replace(List.of(new CloudAssetSummary(REF, "model", "ysm", 12L)));
        AtomicInteger downloads = new AtomicInteger();
        CompletableFuture<Path> first = new CompletableFuture<>();
        CloudAssetMaterializationCoordinator coordinator = new CloudAssetMaterializationCoordinator(
                catalog,
                requested -> {
                    assertSame(REF, requested);
                    downloads.incrementAndGet();
                    return first;
                });

        CompletableFuture<Path> one = coordinator.ensure(REF);
        CompletableFuture<Path> two = coordinator.ensure(REF);
        assertSame(one, two);
        assertEquals(1, downloads.get());

        first.completeExceptionally(new IllegalStateException("download failed"));
        assertThrows(CompletionException.class, one::join);

        CompletableFuture<Path> retry = coordinator.ensure(REF);
        assertEquals(2, downloads.get());
        retry.complete(Path.of("cache-entry"));
    }

    @Test
    void refusesAssetRevisionAbsentFromTrustedCatalog() {
        CloudAssetCatalogStore catalog = new CloudAssetCatalogStore();
        catalog.replace(List.of(new CloudAssetSummary(REF, "model", "ysm", 12L)));
        CloudAssetRef wrongRevision = new CloudAssetRef(
                "cloud-model", 4L, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        CloudAssetMaterializationCoordinator coordinator = new CloudAssetMaterializationCoordinator(
                catalog, ignored -> CompletableFuture.completedFuture(Path.of("must-not-download")));

        assertThrows(CompletionException.class, () -> coordinator.ensure(wrongRevision).join());
    }
}
