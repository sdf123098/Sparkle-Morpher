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
    private static final CloudAssetClient.MemoryAsset ASSET =
            new CloudAssetClient.MemoryAsset(new byte[]{1, 2, 3}, "ysm");

    @Test
    void deduplicatesSameRevisionAndAllowsRetryAfterFailure() {
        CloudAssetCatalogStore catalog = new CloudAssetCatalogStore();
        catalog.replace(List.of(new CloudAssetSummary(REF, "model", "ysm", 12L)));
        AtomicInteger downloads = new AtomicInteger();
        CompletableFuture<CloudAssetClient.MemoryAsset> first = new CompletableFuture<>();
        CloudAssetMaterializationCoordinator coordinator = new CloudAssetMaterializationCoordinator(
                catalog,
                requested -> {
                    assertSame(REF, requested);
                    downloads.incrementAndGet();
                    return first;
                });

        CompletableFuture<CloudAssetClient.MemoryAsset> one = coordinator.ensure(REF);
        CompletableFuture<CloudAssetClient.MemoryAsset> two = coordinator.ensure(REF);
        assertSame(one, two);
        assertEquals(1, downloads.get());

        first.completeExceptionally(new IllegalStateException("download failed"));
        assertThrows(CompletionException.class, one::join);

        CompletableFuture<CloudAssetClient.MemoryAsset> retry = coordinator.ensure(REF);
        assertEquals(2, downloads.get());
        retry.complete(ASSET);
    }

    @Test
    void closeCancelsPendingDownloadAndRejectsBothEntryPoints() {
        var catalog = new CloudAssetCatalogStore();
        catalog.upsert(new CloudAssetSummary(REF, "model", "ysm", 12));
        var download = new CompletableFuture<CloudAssetClient.MemoryAsset>();
        var coordinator = new CloudAssetMaterializationCoordinator(catalog, ref -> download, ref -> true);
        var pending = coordinator.ensure(REF);
        coordinator.close(); coordinator.close();
        org.junit.jupiter.api.Assertions.assertTrue(pending.isCancelled());
        org.junit.jupiter.api.Assertions.assertTrue(download.isCancelled());
        assertEquals(0, coordinator.inFlightCount());
        org.junit.jupiter.api.Assertions.assertTrue(coordinator.ensure(REF).isCompletedExceptionally());
        org.junit.jupiter.api.Assertions.assertTrue(coordinator.ensurePreviouslyApplied(REF).isCompletedExceptionally());
        org.junit.jupiter.api.Assertions.assertFalse(download.complete(ASSET));
    }

    @Test
    void switchingAccountsNeverSharesTheOldFutureAndCancellationAllowsANewWorld() {
        var catalog = new CloudAssetCatalogStore();
        catalog.upsert(new CloudAssetSummary(REF, "model", "ysm", 12));
        var accountOneDownload = new CompletableFuture<CloudAssetClient.MemoryAsset>();
        var accountTwoDownload = new CompletableFuture<CloudAssetClient.MemoryAsset>();
        var accountOne = new CloudAssetMaterializationCoordinator(catalog, ref -> accountOneDownload);
        var accountTwo = new CloudAssetMaterializationCoordinator(catalog, ref -> accountTwoDownload);
        var one = accountOne.ensure(REF); var two = accountTwo.ensure(REF);
        org.junit.jupiter.api.Assertions.assertNotSame(one, two);
        accountOne.close();
        org.junit.jupiter.api.Assertions.assertFalse(two.isDone());
        accountTwoDownload.complete(ASSET); assertSame(ASSET, two.join());
        var downloads = new AtomicInteger();
        var reusable = new CloudAssetMaterializationCoordinator(catalog, ref -> {
            downloads.incrementAndGet(); return new CompletableFuture<>();
        });
        var previousWorld = reusable.ensure(REF); reusable.cancelPending();
        org.junit.jupiter.api.Assertions.assertTrue(previousWorld.isCancelled());
        var nextWorld = reusable.ensure(REF);
        org.junit.jupiter.api.Assertions.assertNotSame(previousWorld, nextWorld);
        assertEquals(2, downloads.get()); reusable.close();
    }

    @Test
    void refusesAssetRevisionAbsentFromTrustedCatalog() {
        CloudAssetCatalogStore catalog = new CloudAssetCatalogStore();
        catalog.replace(List.of(new CloudAssetSummary(REF, "model", "ysm", 12L)));
        CloudAssetRef wrongRevision = new CloudAssetRef(
                "cloud-model", 4L, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        CloudAssetMaterializationCoordinator coordinator = new CloudAssetMaterializationCoordinator(
                catalog, ignored -> CompletableFuture.completedFuture(ASSET));

        assertThrows(CompletionException.class, () -> coordinator.ensure(wrongRevision).join());
    }

    @Test
    void restoresAnExactlyAppliedOlderRevisionWithoutDowngradingCatalog() {
        var old = new CloudAssetRef("cloud-model", 2, "b".repeat(64));
        var catalog = new CloudAssetCatalogStore();
        catalog.upsert(new CloudAssetSummary(REF, "current", "ysm", 12));
        var downloads = new AtomicInteger();
        var coordinator = new CloudAssetMaterializationCoordinator(catalog, ref -> {
            downloads.incrementAndGet(); return CompletableFuture.completedFuture(ASSET);
        }, old::equals);
        assertThrows(CompletionException.class, () -> coordinator.ensure(old).join());
        assertSame(ASSET, coordinator.ensurePreviouslyApplied(old).join());
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
            downloads.incrementAndGet(); return CompletableFuture.completedFuture(ASSET);
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
            downloads.incrementAndGet(); return CompletableFuture.completedFuture(ASSET);
        }, ref -> selection.read("official", false).stream().anyMatch(entry -> entry.ref().equals(ref)));
        assertThrows(CompletionException.class, () -> coordinator.ensurePreviouslyApplied(REF).join());
        assertEquals(0, downloads.get());
        selection.recordApplied("official", asset);
        assertSame(ASSET, coordinator.ensurePreviouslyApplied(REF).join());
    }
}
