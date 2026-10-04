package com.micaftic.morpher.cloud.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

/** Shares asset work, never the per-entity application callback. Used on the client thread. */
public final class CloudEntityModelMaterializer {
    private final Map<String, CompletableFuture<String>> imports = new ConcurrentHashMap<>();
    public CompletableFuture<String> load(CloudEntityPresenceClient.Entry entry, Predicate<String> resident,
                                           Function<CloudEntityPresenceClient.Entry, CompletableFuture<String>> loader) {
        String id = entry.selection().runtimeModelId();
        var existing = imports.get(id);
        if (existing != null && (!existing.isDone() || !existing.isCompletedExceptionally() && resident.test(id))) return existing;
        var future = loader.apply(entry);
        imports.put(id, future);
        future.whenComplete((ignored, error) -> { if (error != null) imports.remove(id, future); });
        return future;
    }
    public void clear() { imports.clear(); }
}
