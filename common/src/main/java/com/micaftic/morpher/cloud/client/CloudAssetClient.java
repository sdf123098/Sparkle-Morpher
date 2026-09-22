package com.micaftic.morpher.cloud.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Typed P1 directory and original-byte operations. */
public final class CloudAssetClient {

    private final CloudHttpClient http;

    public CloudAssetClient(CloudHttpClient http) {
        this.http = java.util.Objects.requireNonNull(http, "http");
    }

    public CompletableFuture<List<CloudAssetSummary>> list() {
        return http.getJson("/v1/assets").thenApply(this::parseList);
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

    private List<CloudAssetSummary> parseList(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonArray()) {
                throw new IllegalArgumentException("Cloud asset catalog must be an array");
            }
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
                        object.get("byte_length").getAsLong()));
            }
            return List.copyOf(entries);
        } catch (RuntimeException e) {
            throw new CloudHttpException(200, com.micaftic.morpher.core.api.network.state.CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud asset catalog");
        }
    }

    private CloudAssetSummary parseSummary(com.google.gson.JsonObject object) {
        CloudAssetRef ref = new CloudAssetRef(object.get("asset_id").getAsString(), object.get("revision").getAsLong(), object.get("raw_sha256").getAsString());
        return new CloudAssetSummary(ref, object.get("name").getAsString(), object.get("format").getAsString(), object.get("byte_length").getAsLong());
    }
}
