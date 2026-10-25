package com.micaftic.morpher.client;

import com.micaftic.morpher.cloud.client.CloudAssetClient;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CloudRepeatLoadTest {
    @Test
    void residentAndRetainedSourcesReauthorizeWithoutDownloading() {
        AtomicInteger downloads = new AtomicInteger(), permissions = new AtomicInteger();
        for (String layer : new String[]{"resident", "retained source", "parse in progress"}) {
            var asset = CloudRuntimeReuse.materialize(() -> true, () -> true,
                    () -> { downloads.incrementAndGet(); return CompletableFuture.failedFuture(new AssertionError(layer)); },
                    () -> { permissions.incrementAndGet(); return CompletableFuture.completedFuture(true); }).join();
            assertNull(asset.bytes(), layer);
        }
        assertEquals(0, downloads.get());
        assertEquals(3, permissions.get());
    }

    @Test
    void missingOrEvictedSourceFetchesOnceAndColdStartNeverUsesDisk() {
        var content = new CloudAssetClient.MemoryAsset(new byte[]{1}, "ysm");
        AtomicInteger downloads = new AtomicInteger();
        var loaded = CloudRuntimeReuse.materialize(() -> true, () -> false,
                () -> { downloads.incrementAndGet(); return CompletableFuture.completedFuture(content); },
                () -> CompletableFuture.failedFuture(new AssertionError("download itself checks ACL"))).join();
        assertSame(content, loaded);
        assertEquals(1, downloads.get());
    }

    @Test
    void revokedResidentCannotSucceedAndDoesNotFallBackToDownload() {
        AtomicInteger downloads = new AtomicInteger();
        var result = CloudRuntimeReuse.materialize(() -> true, () -> true,
                () -> { downloads.incrementAndGet(); return CompletableFuture.completedFuture(null); },
                () -> CompletableFuture.completedFuture(false));
        assertInstanceOf(SecurityException.class, assertThrows(CompletionException.class, result::join).getCause());
        assertEquals(0, downloads.get());
    }

    @Test
    void accountOrWorldChangeDuringAuthorizationPreventsReuse() {
        AtomicBoolean live = new AtomicBoolean(true);
        var permission = new CompletableFuture<Boolean>();
        var result = CloudRuntimeReuse.materialize(live::get, () -> true,
                () -> CompletableFuture.failedFuture(new AssertionError()), () -> permission);
        live.set(false); permission.complete(true);
        assertTrue(result.isCompletedExceptionally());
    }

    @Test
    void previewAndFiveObserversShareOneParseButKeepIndependentResultsAndLeases() {
        CloudModelLoadCoordinator<String> imports = new CloudModelLoadCoordinator<>();
        ClientModelResidency residency = new ClientModelResidency();
        var leases = new ArrayList<RuntimeModelLease>();
        AtomicBoolean previewOpen = new AtomicBoolean(true);
        var preview = new ArrayList<String>();
        var leader = imports.join("model", previewOpen::get, preview::add);
        assertNotNull(leader);
        AtomicInteger parses = new AtomicInteger(); parses.incrementAndGet();
        for (int i = 0; i < 5; i++) assertNull(imports.join("model", () -> true, error -> {
            assertNull(error); leases.add(residency.acquire("model"));
        }));
        previewOpen.set(false);
        assertTrue(imports.isCurrent(leader), "closing preview must not cancel the five observers");
        imports.finish(leader, null, "cancelled");
        assertEquals(1, parses.get());
        assertEquals(java.util.List.of("cancelled"), preview);
        assertEquals(5, residency.consumerCount("model"));
        leases.forEach(RuntimeModelLease::close);
        assertEquals(0, residency.consumerCount("model"));
    }

    @Test
    void obsoleteRevisionAndCancelledTicketCannotPublishOrSettleReplacement() {
        var imports = new CloudModelLoadCoordinator<String>();
        var results = new ArrayList<String>();
        var old = imports.join("revision-1", () -> true, results::add);
        imports.cancelAll("world changed");
        var next = imports.join("revision-2", () -> true, results::add);
        assertFalse(imports.isCurrent(old));
        imports.finish(old, "stale success", "cancelled");
        assertTrue(imports.isCurrent(next));
        imports.finish(next, "new success", "cancelled");
        assertEquals(java.util.List.of("world changed", "new success"), results);
    }

    @Test
    void accessRevocationInvalidatesEveryConsumerLease() {
        var residency = new ClientModelResidency();
        var first = residency.acquire("cloud-model");
        var second = residency.acquire("cloud-model");
        residency.revoke("cloud-model");
        assertFalse(first.isValid()); assertFalse(second.isValid());
        assertEquals(0, residency.consumerCount("cloud-model"));
        first.close(); second.close();
        assertEquals(0, residency.consumerCount("cloud-model"));
    }
}
