package com.micaftic.morpher.client;

import com.micaftic.morpher.cloud.client.CloudAssetClient;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** The L0/L1 decision shared by UI application and previews; this never parses or stores a model. */
public final class CloudRuntimeReuse {
    private CloudRuntimeReuse() {}

    public static CompletableFuture<CloudAssetClient.MemoryAsset> materialize(BooleanSupplier current,
            BooleanSupplier reusable, Supplier<CompletableFuture<CloudAssetClient.MemoryAsset>> download,
            Supplier<CompletableFuture<Boolean>> authorize) {
        if (!current.getAsBoolean()) return CompletableFuture.failedFuture(new CancellationException("Cloud request superseded"));
        try {
            if (!reusable.getAsBoolean()) return download.get();
            return authorize.get().thenCompose(allowed -> {
                if (!Boolean.TRUE.equals(allowed)) return CompletableFuture.failedFuture(new SecurityException("Cloud display access denied"));
                if (!current.getAsBoolean()) return CompletableFuture.failedFuture(new CancellationException("Cloud request superseded"));
                return reusable.getAsBoolean() ? CompletableFuture.completedFuture(new CloudAssetClient.MemoryAsset(null, null)) : download.get();
            });
        } catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
    }
}
