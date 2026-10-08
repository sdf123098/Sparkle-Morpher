package com.micaftic.morpher.cloud.client;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Revalidates both the target appearance and the exact asset revision. */
public final class CloudAppearanceAuthorization {
    private CloudAppearanceAuthorization() {}

    public static CompletableFuture<Boolean> check(CloudScopeClient scopes, CloudAssetClient assets,
                                                   CloudScopeClient.CloudAppearance expected) {
        try { CloudScopeClient.segment(expected.targetId()); }
        catch (IllegalArgumentException invalid) { return CompletableFuture.completedFuture(false); }
        return scopes.getAppearance(expected.targetId()).thenCompose(actual -> {
            if (!Objects.equals(actual, expected)) return CompletableFuture.completedFuture(false);
            if (expected.assetId() == null || expected.assetId().isBlank())
                return CompletableFuture.completedFuture(true);
            if (expected.assetRevision() == null || expected.rawSha256() == null)
                return CompletableFuture.completedFuture(false);
            try {
                return assets.authorizeDisplay(new CloudAssetRef(expected.assetId(), expected.assetRevision(), expected.rawSha256()));
            } catch (IllegalArgumentException invalid) {
                return CompletableFuture.completedFuture(false);
            }
        });
    }
}
