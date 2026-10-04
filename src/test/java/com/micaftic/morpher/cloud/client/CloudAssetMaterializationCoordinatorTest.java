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

    @Test
    void restoresAnExactlyAppliedOlderRevisionWithoutDowngradingCatalog() {
        var old = new CloudAssetRef("cloud-model", 2, "b".repeat(64));
        var catalog = new CloudAssetCatalogStore();
        catalog.upsert(new CloudAssetSummary(REF, "current", "ysm", 12));
        var downloads = new AtomicInteger();
        var coordinator = new CloudAssetMaterializationCoordinator(catalog, ref -> {
            downloads.incrementAndGet(); return CompletableFuture.completedFuture(Path.of("verified-old-bytes"));
        }, old::equals);
        assertThrows(CompletionException.class, () -> coordinator.ensure(old).join());
        assertEquals(Path.of("verified-old-bytes"), coordinator.ensurePreviouslyApplied(old).join());
        assertEquals(REF, catalog.get("cloud-model").ref());
        assertThrows(CompletionException.class, () -> coordinator.ensurePreviouslyApplied(new CloudAssetRef("cloud-model", 1, "b".repeat(64))).join());
        assertThrows(CompletionException.class, () -> coordinator.ensurePreviouslyApplied(new CloudAssetRef("cloud-model", 2, "c".repeat(64))).join());
        assertEquals(1, downloads.get());
    }

    @Test
    void historicalAccessIsDeniedByDefaultAndDoesNotHideCurrentServerFailures() {
        var catalog = new CloudAssetCatalogStore();
        var downloads = new AtomicInteger();
        var denied = new CloudAssetMaterializationCoordinator(catalog, ref -> {
            downloads.incrementAndGet(); return CompletableFuture.completedFuture(Path.of("must-not-download"));
        });
        assertThrows(CompletionException.class, () -> denied.ensurePreviouslyApplied(REF).join());
        assertEquals(0, downloads.get());
        var coordinator = new CloudAssetMaterializationCoordinator(catalog,
                ref -> CompletableFuture.failedFuture(new IllegalStateException("HTTP 403: current account has no access")), REF::equals);
        var failure = assertThrows(CompletionException.class, () -> coordinator.ensurePreviouslyApplied(REF).join());
        assertEquals("HTTP 403: current account has no access", failure.getCause().getMessage());
        assertEquals(0, coordinator.inFlightCount());
    }
    @Test
    void onlyAFavoriteDoesNotAuthorizeHistoricalMaterialization(@org.junit.jupiter.api.io.TempDir Path root) {
        var selection = new CloudModelSelectionStore.Index(root.resolve("selection.json"));
        var asset = new CloudAssetSummary(REF, "skin", "ysm", 12);
        selection.setFavorite("official", asset, true);
        var downloads = new AtomicInteger();
        var coordinator = new CloudAssetMaterializationCoordinator(new CloudAssetCatalogStore(), ref -> {
            downloads.incrementAndGet(); return CompletableFuture.completedFuture(Path.of("verified-cache"));
        }, ref -> selection.read("official", false).stream().anyMatch(entry -> entry.ref().equals(ref)));
        assertThrows(CompletionException.class, () -> coordinator.ensurePreviouslyApplied(REF).join());
        assertEquals(0, downloads.get());
        selection.recordApplied("official", asset);
        assertEquals(Path.of("verified-cache"), coordinator.ensurePreviouslyApplied(REF).join());
    }
}
