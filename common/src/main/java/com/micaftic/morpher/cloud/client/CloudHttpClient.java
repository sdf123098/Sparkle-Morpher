package com.micaftic.morpher.cloud.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micaftic.morpher.cloud.CloudInstanceConfig;
import com.micaftic.morpher.core.api.network.state.CloudConnectionStatus;
import com.micaftic.morpher.core.api.network.state.CloudErrorCode;
import com.micaftic.morpher.core.api.network.state.CloudState;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.micaftic.morpher.core.api.network.upload.ModelUploadTransport;

/**
 * Asynchronous HTTPS boundary for the selected Cloud instance.
 *
 * <p>Only the configured instance origin is used. Provider and object-store
 * URLs are discovered from the server but are never accepted as request
 * destinations by this class.</p>
 */
public final class CloudHttpClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration UPLOAD_TIMEOUT = Duration.ofMinutes(5);

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

    /** Initial discovery sends no credentials and keeps the user-entered origin authoritative. */
    public static CompletableFuture<CloudInstanceRegistry.CloudInstanceProfile> discoverProfile(String address, String name) {
        return discoverProfile(address, name, HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build());
    }

    static CompletableFuture<CloudInstanceRegistry.CloudInstanceProfile> discoverProfile(String address, String name, HttpClient httpClient) {
        String normalized = Objects.requireNonNull(address, "address").trim();
        if (!normalized.contains("://")) normalized = "https://" + normalized;
        CloudInstanceConfig probe = CloudInstanceConfig.v1("discovery", URI.create(normalized));
        return new CloudHttpClient(probe, httpClient).getJson("/v1/instance").thenApply(body -> {
            try {
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                CloudInstanceConfig expected = CloudInstanceConfig.v1(requiredString(root, "instance_id"), probe.origin());
                CloudInstanceInfo info = parseInstanceResponse(expected, body);
                String label = name == null || name.isBlank() ? probe.origin().getHost() : name.trim();
                return new CloudInstanceRegistry.CloudInstanceProfile(info.config(), label);
            } catch (CloudHttpException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud instance response");
            }
        });
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

    /** Bounded metadata transport for negotiated entity capabilities. */
    CompletableFuture<String> visualJson(String method, String path, String jsonBody, int maxResponseBytes) {
        if (!java.util.Set.of("POST", "PUT", "DELETE").contains(method)
                || maxResponseBytes < 1 || maxResponseBytes > 16 * 1024 * 1024) {
            throw new IllegalArgumentException("Invalid visual request budget");
        }
        HttpRequest request = requestBuilder(path).timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json").header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build();
        return httpClient.sendAsync(request, info -> new BoundedJsonSubscriber(maxResponseBytes)).thenCompose(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) return CompletableFuture.failedFuture(httpFailure(response));
            try {
                String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(response.body())).toString();
                return CompletableFuture.completedFuture(text);
            } catch (java.nio.charset.CharacterCodingException error) { return CompletableFuture.failedFuture(error); }
        });
    }

    static final class BoundedJsonSubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int maximum;
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private java.util.concurrent.Flow.Subscription subscription;
        BoundedJsonSubscriber(int maximum) { this.maximum = maximum; }
        public java.util.concurrent.CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(java.util.concurrent.Flow.Subscription next) { subscription = next; next.request(1); }
        public void onNext(java.util.List<java.nio.ByteBuffer> buffers) {
            long size = bytes.size();
            for (var buffer : buffers) size += buffer.remaining();
            if (size > maximum) {
                subscription.cancel();
                result.completeExceptionally(new CloudHttpException(200, CloudErrorCode.MESSAGE_TOO_LARGE, "Visual response exceeded its negotiated budget"));
                return;
            }
            for (var buffer : buffers) {
                byte[] chunk = new byte[Math.min(8192, buffer.remaining())];
                while (buffer.hasRemaining()) {
                    int length = Math.min(chunk.length, buffer.remaining());
                    buffer.get(chunk, 0, length); bytes.write(chunk, 0, length);
                }
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }

    public CompletableFuture<HttpResponse<byte[]>> uploadAsset(byte[] content, String assetId, String assetName, String assetFormat, String rawSha256) {
        return uploadAsset(content, assetId, assetName, assetFormat, rawSha256, java.util.UUID.randomUUID().toString());
    }

    public CompletableFuture<HttpResponse<byte[]>> uploadAsset(byte[] content, String assetId, String assetName, String assetFormat, String rawSha256, String requestId) {
        return uploadAsset(content, assetId, assetName, assetFormat, rawSha256, requestId, "PRIVATE");
    }

    public CompletableFuture<HttpResponse<byte[]>> uploadAsset(byte[] content, String assetId, String assetName, String assetFormat, String rawSha256, String requestId, String visibility) {
        Objects.requireNonNull(content, "content");
        HttpRequest.Builder builder = requestBuilder("/v1/assets")
                .timeout(UPLOAD_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/octet-stream")
                .header("Idempotency-Key", requiredHeader(requestId, "requestId"))
                .header("X-Asset-Metadata-Encoding", "utf-8-percent")
                .header("X-Asset-Id", metadataHeader(assetId, "assetId"))
                .header("X-Asset-Name", metadataHeader(assetName, "assetName"))
                .header("X-Asset-Format", requiredHeader(assetFormat, "assetFormat"))
                .header("X-Asset-Sha256", requiredHeader(rawSha256, "rawSha256"))
                .header("X-Asset-Visibility", requiredHeader(visibility, "visibility"));
        return httpClient.sendAsync(builder.POST(HttpRequest.BodyPublishers.ofByteArray(content)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    /** Streams a local file into the Cloud request; the file is never materialized as a byte array. */
    public CompletableFuture<HttpResponse<byte[]>> uploadAsset(
            Path source,
            long totalBytes,
            String assetId,
            String assetName,
            String assetFormat,
            String rawSha256,
            String requestId,
            ModelUploadTransport.ProgressListener progress,
            ModelUploadTransport.Cancellation cancellation) {
        return uploadAsset(source, totalBytes, assetId, assetName, assetFormat, rawSha256, requestId, "PRIVATE", progress, cancellation);
    }

    public CompletableFuture<HttpResponse<byte[]>> uploadAsset(
            Path source,
            long totalBytes,
            String assetId,
            String assetName,
            String assetFormat,
            String rawSha256,
            String requestId,
            String visibility,
            ModelUploadTransport.ProgressListener progress,
            ModelUploadTransport.Cancellation cancellation) {
        Objects.requireNonNull(source, "source");
        if (totalBytes <= 0) {
            throw new IllegalArgumentException("totalBytes must be positive");
        }
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(cancellation, "cancellation");
        HttpRequest.Builder builder = requestBuilder("/v1/assets")
                .timeout(UPLOAD_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/octet-stream")
                .header("Idempotency-Key", requiredHeader(requestId, "requestId"))
                .header("X-Asset-Metadata-Encoding", "utf-8-percent")
                .header("X-Asset-Id", metadataHeader(assetId, "assetId"))
                .header("X-Asset-Name", metadataHeader(assetName, "assetName"))
                .header("X-Asset-Format", requiredHeader(assetFormat, "assetFormat"))
                .header("X-Asset-Sha256", requiredHeader(rawSha256, "rawSha256"))
                .header("X-Asset-Visibility", requiredHeader(visibility, "visibility"));
        HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofInputStream(() -> {
            try {
                return new ProgressInputStream(Files.newInputStream(source), totalBytes, progress, cancellation);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to open Cloud upload source", e);
            }
        });
        return httpClient.sendAsync(builder.POST(body).build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    public CompletableFuture<String> putJson(String path, String jsonBody) {
        HttpRequest.Builder builder = requestBuilder(path)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
        return httpClient.sendAsync(
                        builder.PUT(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
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

    /** Rechecks ACL before accepting a resident resource; never buffers model bytes. */
    public CompletableFuture<Boolean> authorizeAsset(String path, String sha256) {
        String etag = "\"" + sha256 + "\"";
        var request = requestBuilder(path).timeout(REQUEST_TIMEOUT)
                .header("If-None-Match", etag).GET().build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).thenApply(response -> {
            try (var body = response.body()) {
                return (response.statusCode() == 304 || response.statusCode() == 200)
                        && etag.equals(response.headers().firstValue("ETag").orElse(null));
            } catch (IOException failure) {
                return false;
            }
        });
    }

    private HttpRequest.Builder requestBuilder(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(instance.apiUri(path));
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        return builder;
    }

    static CloudHttpException httpFailure(HttpResponse<?> response) {
        String body = response.body() instanceof byte[] bytes
                ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                : String.valueOf(response.body());
        String message = "Cloud HTTP request failed";
        try {
            JsonElement value = JsonParser.parseString(body).getAsJsonObject().get("message");
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String detail = value.getAsString().replaceAll("[\\p{Cntrl}]", " ").trim();
                if (!detail.isBlank()) message = detail.substring(0, Math.min(2048, detail.length()));
            }
        } catch (RuntimeException ignored) { }
        return new CloudHttpException(response.statusCode(), errorCodeFrom(body), message);
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
            java.util.Set<String> capabilities = new java.util.HashSet<>();
            if (root.has("capabilities")) {
                if (!root.get("capabilities").isJsonArray() || root.getAsJsonArray("capabilities").size() > 128) throw new IllegalArgumentException("Invalid capabilities");
                for (JsonElement value : root.getAsJsonArray("capabilities")) {
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().length() > 128) throw new IllegalArgumentException("Invalid capability");
                    capabilities.add(value.getAsString());
                }
            }
            return new CloudInstanceInfo(
                    advertised,
                    positiveLimit(limits, "max_message_bytes", 64 * 1024),
                    positiveLimit(limits, "max_snapshot_bytes", 16 * 1024 * 1024),
                    positiveLimit(limits, "max_snapshot_chunk_bytes", 256 * 1024),
                    positiveLimit(limits, "max_asset_bytes", 128L * 1024 * 1024),
                    positiveLimit(limits, "max_subscriptions", 128),
                    positiveLimit(limits, "heartbeat_interval_seconds", 15),
                    positiveLimit(limits, "heartbeat_ttl_seconds", 45),
                    capabilities.contains("player_motion_v1"),
                    parseAuthCapabilities(root),
                    positiveLimit(limits, "max_entity_query_count", 64), capabilities,
                    positiveLimit(limits, "max_visual_state_bytes", 8192),
                    nonnegativeLimit(limits, "max_visual_variables", 32));
        } catch (CloudHttpException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud instance response");
        }
    }

    private static CloudInstanceInfo.AuthCapabilities parseAuthCapabilities(JsonObject root) {
        if (!root.has("auth")) return null;
        JsonObject auth = root.getAsJsonObject("auth");
        return new CloudInstanceInfo.AuthCapabilities(authFlag(auth, "password_login"), authFlag(auth, "game_identity_login"),
                authFlag(auth, "game_identity_link"), authFlag(auth, "self_registration"));
    }

    private static boolean authFlag(JsonObject auth, String name) {
        if (!auth.has(name)) return true;
        JsonElement value = auth.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Invalid Cloud auth flag: " + name);
        return value.getAsBoolean();
    }

    private static String requiredString(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive() || value.getAsString().isBlank()) {
            throw new IllegalArgumentException("Missing Cloud instance field: " + name);
        }
        return value.getAsString();
    }

    private static long positiveLimit(JsonObject object, String name, long fallback) {
        long value = nonnegativeLimit(object, name, fallback);
        if (value == 0) {
            throw new IllegalArgumentException("Cloud limit must be positive: " + name);
        }
        return value;
    }

    private static long nonnegativeLimit(JsonObject object, String name, long fallback) {
        if (!object.has(name)) return fallback;
        JsonElement value = object.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Invalid Cloud limit: " + name);
        long number = value.getAsBigDecimal().longValueExact();
        if (number < 0) throw new IllegalArgumentException("Negative Cloud limit: " + name);
        return number;
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

    /** Percent-encode UTF-8 metadata, keeping HTTP headers ASCII and literal percent/plus signs unambiguous. */
    private static String metadataHeader(String value, String name) {
        return URLEncoder.encode(requiredHeader(value, name), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String requiredHeader(String value, String name) {
        if (value == null || value.isBlank() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(name + " must be a non-empty single-line value");
        }
        return value;
    }

    private static final class ProgressInputStream extends FilterInputStream {
        private final long totalBytes;
        private final ModelUploadTransport.ProgressListener progress;
        private final ModelUploadTransport.Cancellation cancellation;
        private long sentBytes;

        private ProgressInputStream(InputStream delegate, long totalBytes,
                                    ModelUploadTransport.ProgressListener progress,
                                    ModelUploadTransport.Cancellation cancellation) {
            super(delegate);
            this.totalBytes = totalBytes;
            this.progress = progress;
            this.cancellation = cancellation;
        }

        @Override
        public int read() throws IOException {
            checkCancelled();
            int value = super.read();
            if (value >= 0) {
                report(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            checkCancelled();
            int count = super.read(buffer, offset, length);
            if (count > 0) {
                report(count);
            }
            return count;
        }

        private void report(int count) {
            sentBytes += count;
            progress.onProgress(sentBytes, totalBytes);
        }

        private void checkCancelled() throws IOException {
            if (cancellation.isCancelled()) {
                throw new IOException("Cloud upload cancelled");
            }
        }
    }
}
