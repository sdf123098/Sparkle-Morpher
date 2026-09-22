package com.micaftic.morpher.core.api.network.upload;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Cloud model upload boundary.
 *
 * <p>The upload is one asynchronous operation. Implementations must stream
 * {@link UploadMetadata#source()} and must not require the caller to retain a
 * complete byte array or drive a per-tick start/chunk/finish state machine.</p>
 */
public interface ModelUploadTransport {

    default CompletableFuture<UploadResult> upload(
            UploadMetadata metadata,
            Path source,
            ProgressListener progress,
            Cancellation cancellation) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "This transport still belongs to the legacy packet compatibility path"));
    }

    /** Immutable upload metadata sent alongside the object stream. */
    record UploadMetadata(
            String assetId,
            String fileName,
            String format,
            String rawSha256,
            long totalBytes
    ) {
        public UploadMetadata {
            assetId = required(assetId, "assetId");
            fileName = required(fileName, "fileName");
            format = required(format, "format");
            rawSha256 = required(rawSha256, "rawSha256");
            if (totalBytes <= 0) {
                throw new IllegalArgumentException("totalBytes must be positive");
            }
        }

        /** The source path is deliberately supplied to {@link #upload}, not retained here. */
        private static String required(String value, String name) {
            if (value == null || value.isBlank() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                throw new IllegalArgumentException(name + " must be a non-empty single-line value");
            }
            return value;
        }
    }

    /** Stable result returned after the server commits the object and revision. */
    record UploadResult(String assetId, long revision, String rawSha256, long byteLength) {
        public UploadResult {
            Objects.requireNonNull(assetId, "assetId");
            Objects.requireNonNull(rawSha256, "rawSha256");
            if (revision <= 0 || byteLength <= 0) {
                throw new IllegalArgumentException("revision and byteLength must be positive");
            }
        }
    }

    @FunctionalInterface
    interface ProgressListener {
        void onProgress(long sentBytes, long totalBytes);
    }

    @FunctionalInterface
    interface Cancellation {
        boolean isCancelled();
    }

    /**
     * Compatibility hook for the still-being-removed legacy packet path.
     * New Cloud callers must use {@link #upload}.
     */
    @Deprecated
    default boolean isAvailable() {
        return true;
    }

    /** @deprecated legacy packet uploads are not part of the Cloud contract. */
    @Deprecated
    default void sendStart(String modelId, String fileName, int dataLength, String sha256) {
        throw new UnsupportedOperationException("Legacy start/chunk/finish upload is not a Cloud operation");
    }

    /** @deprecated legacy packet uploads are not part of the Cloud contract. */
    @Deprecated
    default void sendChunk(long uploadId, int nextOffset, byte[] data, int dataOffset, int length) {
        throw new UnsupportedOperationException("Legacy start/chunk/finish upload is not a Cloud operation");
    }

    /** @deprecated legacy packet uploads are not part of the Cloud contract. */
    @Deprecated
    default void sendFinish(long uploadId) {
        throw new UnsupportedOperationException("Legacy start/chunk/finish upload is not a Cloud operation");
    }
}
