package com.micaftic.morpher.cloud.client;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Checks context and privacy on the client thread before every Cloud write stage. */
public final class CloudEntityPublicationGuard {
    private CloudEntityPublicationGuard() {}
    public static <T> CompletableFuture<T> run(Consumer<Runnable> executor, BooleanSupplier allowed,
                                               Supplier<CompletableFuture<T>> request) {
        var result = new CompletableFuture<T>();
        executor.accept(() -> {
            if (!allowed.getAsBoolean()) { result.completeExceptionally(new CancellationException("Entity selection is no longer current")); return; }
            try {
                request.get().whenComplete((value, error) -> {
                    if (error == null) result.complete(value); else result.completeExceptionally(error);
                });
            } catch (RuntimeException error) { result.completeExceptionally(error); }
        });
        return result;
    }
}
