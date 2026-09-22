package com.micaftic.morpher.cloud.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micaftic.morpher.cloud.CloudInstanceConfig;
import com.micaftic.morpher.core.api.network.state.CloudConnectionStatus;
import com.micaftic.morpher.core.api.network.state.CloudErrorCode;
import com.micaftic.morpher.core.api.network.state.CloudState;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Asynchronous HTTPS boundary for the selected Cloud instance.
 *
 * <p>Only the configured instance origin is used. Provider and object-store
 * URLs are discovered from the server but are never accepted as request
 * destinations by this class.</p>
 */
public final class CloudHttpClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final CloudInstanceConfig instance;
    private final HttpClient httpClient;
    private final String accessToken;

    public CloudHttpClient(CloudInstanceConfig instance) {
        this(instance, HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(), null);
    }

    CloudHttpClient(CloudInstanceConfig instance, HttpClient httpClient) {
        this(instance, httpClient, null);
    }

    public CloudHttpClient(CloudInstanceConfig instance, String accessToken) {
        this(instance, HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(), accessToken);
    }

    CloudHttpClient(CloudInstanceConfig instance, HttpClient httpClient, String accessToken) {
        this.instance = Objects.requireNonNull(instance, "instance");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.accessToken = accessToken == null || accessToken.isBlank() ? null : accessToken;
    }

    public CloudInstanceConfig instance() {
        return instance;
    }

    public CompletableFuture<CloudInstanceInfo> discoverInstance() {
        CloudState.setStatus(CloudConnectionStatus.CONNECTING, CloudErrorCode.NONE, instance.instanceId());
        HttpRequest request = requestBuilder("/v1/instance")
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        return CompletableFuture.failedFuture(new CloudHttpException(
                                response.statusCode(), errorCodeFrom(response.body()), "Cloud instance discovery failed"));
                    }
                    try {
                        return CompletableFuture.completedFuture(parseInstanceResponse(instance, response.body()));
                    } catch (RuntimeException e) {
                        return CompletableFuture.failedFuture(e);
                    }
                })
                .whenComplete((result, failure) -> {
                    if (failure == null) {
                        CloudState.setStatus(CloudConnectionStatus.READY, CloudErrorCode.NONE, result.config().instanceId());
                    } else {
                        CloudState.setStatus(CloudConnectionStatus.DISCONNECTED, errorCodeFrom(failure), instance.instanceId());
                    }
                });
    }

    public CompletableFuture<String> getJson(String path) {
        return getBytes(path, null, null).thenCompose(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return CompletableFuture.failedFuture(httpFailure(response));
            }
            return CompletableFuture.completedFuture(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8));
        });
    }

    public CompletableFuture<String> postJson(String path, String jsonBody) {
        HttpRequest.Builder builder = requestBuilder(path)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
        return httpClient.sendAsync(
                        builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                        HttpResponse.BodyHandlers.ofByteArray())
                .thenCompose(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        return CompletableFuture.failedFuture(httpFailure(response));
                    }
                    return CompletableFuture.completedFuture(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    public CompletableFuture<HttpResponse<byte[]>> getBytes(String path, String range, String ifNoneMatch) {
        HttpRequest.Builder builder = requestBuilder(path).timeout(REQUEST_TIMEOUT).header("Accept", "application/octet-stream");
        if (range != null && !range.isBlank()) {
            builder.header("Range", range);
        }
        if (ifNoneMatch != null && !ifNoneMatch.isBlank()) {
            builder.header("If-None-Match", ifNoneMatch);
        }
        return httpClient.sendAsync(builder.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpRequest.Builder requestBuilder(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(instance.apiUri(path));
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        return builder;
    }

    private static CloudHttpException httpFailure(HttpResponse<?> response) {
        String body = response.body() instanceof byte[] bytes
                ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                : String.valueOf(response.body());
        return new CloudHttpException(response.statusCode(), errorCodeFrom(body), "Cloud HTTP request failed");
    }

    static CloudInstanceInfo parseInstanceResponse(CloudInstanceConfig expected, String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            String instanceId = requiredString(root, "instance_id");
            String originText = requiredString(root, "origin");
            String protocol = requiredString(root, "protocol");
            CloudInstanceConfig advertised = CloudInstanceConfig.v1(instanceId, URI.create(originText));
            if (!expected.instanceId().equals(advertised.instanceId()) || !expected.origin().equals(advertised.origin())) {
                throw new CloudHttpException(200, CloudErrorCode.INSTANCE_MISMATCH, "Cloud response changed the trusted instance");
            }
            if (!CloudInstanceConfig.PROTOCOL_V1.equals(protocol)) {
                throw new CloudHttpException(200, CloudErrorCode.PROTOCOL_UNSUPPORTED, "Cloud instance protocol is unsupported");
            }
            JsonObject limits = root.has("limits") && root.get("limits").isJsonObject() ? root.getAsJsonObject("limits") : new JsonObject();
            return new CloudInstanceInfo(
                    advertised,
                    positiveLimit(limits, "max_message_bytes", 64 * 1024),
                    positiveLimit(limits, "max_snapshot_bytes", 16 * 1024 * 1024),
                    positiveLimit(limits, "max_snapshot_chunk_bytes", 256 * 1024),
                    positiveLimit(limits, "max_asset_bytes", 128L * 1024 * 1024),
                    positiveLimit(limits, "max_subscriptions", 128),
                    positiveLimit(limits, "heartbeat_interval_seconds", 15),
                    positiveLimit(limits, "heartbeat_ttl_seconds", 45));
        } catch (CloudHttpException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud instance response");
        }
    }

    private static String requiredString(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive() || value.getAsString().isBlank()) {
            throw new IllegalArgumentException("Missing Cloud instance field: " + name);
        }
        return value.getAsString();
    }

    private static long positiveLimit(JsonObject object, String name, long fallback) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()) {
            return fallback;
        }
        long value = object.get(name).getAsLong();
        if (value <= 0) {
            throw new IllegalArgumentException("Cloud limit must be positive: " + name);
        }
        return value;
    }

    private static CloudErrorCode errorCodeFrom(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        if (current instanceof CloudHttpException cloudFailure) {
            return cloudFailure.errorCode();
        }
        return CloudErrorCode.INTERNAL;
    }

    private static CloudErrorCode errorCodeFrom(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            String code = root.has("code") ? root.get("code").getAsString() : "INTERNAL";
            return CloudErrorCode.valueOf(code);
        } catch (RuntimeException ignored) {
            return CloudErrorCode.INTERNAL;
        }
    }
}
