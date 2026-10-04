package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CloudEntityModelMaterializerTest {
    private CloudEntityPresenceClient.Entry entry(long revision) {
        return new CloudEntityPresenceClient.Entry(UUID.randomUUID(), CloudEntityProvider.Kind.MAID, "target", 1, 1,
                new CloudPlayerSelection("official", "0".repeat(64), new CloudAssetRef("asset", revision, "a".repeat(64)), "ysm", "default"));
    }
    @Test void sameAssetDownloadCompletesEveryEntityConsumer() {
        var cache = new CloudEntityModelMaterializer(); var download = new CompletableFuture<String>();
        var calls = new AtomicInteger(); var applied = new AtomicInteger();
        var first = cache.load(entry(2), id -> true, value -> { calls.incrementAndGet(); return download; });
        var second = cache.load(entry(2), id -> true, value -> { calls.incrementAndGet(); return download; });
        first.thenAccept(value -> applied.incrementAndGet()); second.thenAccept(value -> applied.incrementAndGet());
        assertEquals(1, calls.get()); assertEquals(0, applied.get());
        download.complete("ready"); assertEquals(2, applied.get());
    }
    @Test void preservesExactOldRevisionAndRetriesDeniedDownloads() {
        var cache = new CloudEntityModelMaterializer(); var calls = new AtomicInteger();
        cache.load(entry(1), id -> true, value -> { calls.incrementAndGet(); return CompletableFuture.completedFuture("old"); }).join();
        cache.load(entry(2), id -> true, value -> { calls.incrementAndGet(); return CompletableFuture.completedFuture("new"); }).join();
        cache.load(entry(3), id -> true, value -> { calls.incrementAndGet(); return CompletableFuture.failedFuture(new IllegalStateException("403")); });
        assertEquals("retry", cache.load(entry(3), id -> true, value -> { calls.incrementAndGet(); return CompletableFuture.completedFuture("retry"); }).join());
        assertEquals(4, calls.get());
    }
    @Test void worldResetAndModelEvictionRequireNewAuthorizedLoad() {
        var cache = new CloudEntityModelMaterializer(); var model = entry(1); var calls = new AtomicInteger();
        cache.load(model, id -> true, value -> { calls.incrementAndGet(); return CompletableFuture.completedFuture("first"); }).join();
        cache.load(model, id -> false, value -> { calls.incrementAndGet(); return CompletableFuture.completedFuture("evicted"); }).join();
        cache.clear();
        cache.load(model, id -> true, value -> { calls.incrementAndGet(); return CompletableFuture.completedFuture("new-session"); }).join();
        assertEquals(3, calls.get());
    }
}
