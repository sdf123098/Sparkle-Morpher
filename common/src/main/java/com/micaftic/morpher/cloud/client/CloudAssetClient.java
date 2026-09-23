package com.micaftic.morpher.cloud.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.micaftic.morpher.core.api.network.upload.ModelUploadTransport;

/** Typed P1 directory and original-byte operations. */
public final class CloudAssetClient {

    private final CloudHttpClient http;

    public CloudAssetClient(CloudHttpClient http) {
        this.http = java.util.Objects.requireNonNull(http, "http");
    }

    public CompletableFuture<List<CloudAssetSummary>> list() {
        return listPage("accessible", "", null, 80).thenApply(CloudAssetPage::entries);
    }

    public CompletableFuture<CloudAssetPage> listPage(String scope, String query, String cursor, int limit) {
        int boundedLimit = Math.max(1, Math.min(80, limit));
        StringBuilder path = new StringBuilder("/v1/assets?scope=")
                .append(encode(scope == null || scope.isBlank() ? "accessible" : scope))
                .append("&limit=").append(boundedLimit);
        if (query != null && !query.isBlank()) path.append("&q=").append(encode(query.trim()));
        if (cursor != null && !cursor.isBlank()) path.append("&after=").append(encode(cursor));
        return http.getJson(path.toString()).thenApply(this::parsePage);
    }

    /**
     * Fetches original bytes. The caller must verify the response SHA-256
     * against {@link CloudAssetRef#rawSha256()} before replacing a cache file.
     */
    public CompletableFuture<HttpResponse<byte[]>> download(CloudAssetRef ref, String range, String ifNoneMatch) {
        return http.getBytes(ref.contentPath(), range, ifNoneMatch);
    }

    public CompletableFuture<CloudAssetSummary> upload(byte[] content, String assetId, String assetName, String assetFormat, String rawSha256) {
        return upload(content, assetId, assetName, assetFormat, rawSha256, java.util.UUID.randomUUID().toString());
    }

    public CompletableFuture<CloudAssetSummary> upload(byte[] content, String assetId, String assetName, String assetFormat, String rawSha256, String requestId) {
        return http.uploadAsset(content, assetId, assetName, assetFormat, rawSha256, requestId).thenCompose(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return CompletableFuture.failedFuture(new CloudHttpException(response.statusCode(), com.micaftic.morpher.core.api.network.state.CloudErrorCode.INTERNAL, "Cloud asset upload failed"));
            }
            try {
                return CompletableFuture.completedFuture(parseSummary(JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject()));
            } catch (RuntimeException failure) {
                return CompletableFuture.failedFuture(new CloudHttpException(response.statusCode(), com.micaftic.morpher.core.api.network.state.CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud asset upload response"));
            }
        });
    }

    public CompletableFuture<CloudAssetSummary> upload(
            Path source,
            String assetId,
            String assetName,
            String assetFormat,
            String rawSha256,
            String requestId,
            ModelUploadTransport.ProgressListener progress,
            ModelUploadTransport.Cancellation cancellation) {
        return upload(source, assetId, assetName, assetFormat, rawSha256, requestId, "PRIVATE", progress, cancellation);
    }

    public CompletableFuture<CloudAssetSummary> upload(
            Path source,
            String assetId,
            String assetName,
            String assetFormat,
            String rawSha256,
            String requestId,
            String visibility,
            ModelUploadTransport.ProgressListener progress,
            ModelUploadTransport.Cancellation cancellation) {
        final long length;
        try {
            length = Files.size(source);
        } catch (java.io.IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        return http.uploadAsset(source, length, assetId, assetName, assetFormat, rawSha256, requestId, visibility, progress, cancellation)
                .thenCompose(this::parseUploadResponse);
    }

    private CompletableFuture<CloudAssetSummary> parseUploadResponse(HttpResponse<byte[]> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return CompletableFuture.failedFuture(new CloudHttpException(response.statusCode(), com.micaftic.morpher.core.api.network.state.CloudErrorCode.INTERNAL, "Cloud asset upload failed"));
        }
        try {
            return CompletableFuture.completedFuture(parseSummary(JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject()));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(new CloudHttpException(response.statusCode(), com.micaftic.morpher.core.api.network.state.CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud asset upload response"));
        }
    }

    private CloudAssetPage parsePage(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (root.isJsonArray()) {
                return new CloudAssetPage(parseEntries(root), null, false);
            }
            if (!root.isJsonObject()) throw new IllegalArgumentException("Cloud asset catalog must be an object");
            var object = root.getAsJsonObject();
            JsonElement entriesElement = object.get("entries");
            if (entriesElement == null || !entriesElement.isJsonArray()) throw new IllegalArgumentException("Cloud asset page entries must be an array");
            String next = object.has("next_cursor") && !object.get("next_cursor").isJsonNull() ? object.get("next_cursor").getAsString() : null;
            boolean hasMore = object.has("has_more") && object.get("has_more").getAsBoolean();
            return new CloudAssetPage(parseEntries(entriesElement), next, hasMore);
        } catch (RuntimeException e) {
            throw new CloudHttpException(200, com.micaftic.morpher.core.api.network.state.CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud asset catalog");
        }
    }

    private List<CloudAssetSummary> parseEntries(JsonElement root) {
        List<CloudAssetSummary> entries = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("Cloud asset catalog entry must be an object");
                }
                var object = element.getAsJsonObject();
                CloudAssetRef ref = new CloudAssetRef(
                        object.get("asset_id").getAsString(),
                        object.get("revision").getAsLong(),
                        object.get("raw_sha256").getAsString());
                entries.add(new CloudAssetSummary(
                        ref,
                        object.get("name").getAsString(),
                        object.get("format").getAsString(),
                        object.get("byte_length").getAsLong(),
                        object.has("visibility") ? object.get("visibility").getAsString() : "PRIVATE"));
        }
        return List.copyOf(entries);
    }

    private CloudAssetSummary parseSummary(com.google.gson.JsonObject object) {
        CloudAssetRef ref = new CloudAssetRef(object.get("asset_id").getAsString(), object.get("revision").getAsLong(), object.get("raw_sha256").getAsString());
        return new CloudAssetSummary(ref, object.get("name").getAsString(), object.get("format").getAsString(), object.get("byte_length").getAsLong(),
                object.has("visibility") ? object.get("visibility").getAsString() : "PRIVATE");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
