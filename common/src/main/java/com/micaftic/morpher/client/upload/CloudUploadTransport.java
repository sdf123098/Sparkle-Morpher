package com.micaftic.morpher.client.upload;

import com.micaftic.morpher.cloud.client.CloudAssetClient;
import com.micaftic.morpher.cloud.client.CloudAssetSummary;
import com.micaftic.morpher.core.api.network.upload.ModelUploadTransport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Cloud upload transport backed by the HTTPS asset API.
 */
public final class CloudUploadTransport implements ModelUploadTransport {

    private final CloudAssetClient assets;

    public CloudUploadTransport(CloudAssetClient assets) {
        this.assets = Objects.requireNonNull(assets, "assets");
    }

    @Override
    public CompletableFuture<UploadResult> upload(UploadMetadata metadata, Path source,
                                                   ProgressListener progress, Cancellation cancellation) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(cancellation, "cancellation");
        try {
            if (Files.size(source) != metadata.totalBytes()) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Upload source length changed"));
            }
        } catch (java.io.IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        return assets.upload(source, metadata.assetId(), metadata.fileName(), metadata.format(), metadata.rawSha256(),
                        UUID.randomUUID().toString(), progress, cancellation)
                .thenApply(this::resultOf);
    }

    private UploadResult resultOf(CloudAssetSummary summary) {
        return new UploadResult(summary.ref().assetId(), summary.ref().revision(), summary.ref().rawSha256(), summary.byteLength());
    }
}
