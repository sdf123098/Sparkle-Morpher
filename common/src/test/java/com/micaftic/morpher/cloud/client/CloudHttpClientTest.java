package com.micaftic.morpher.cloud.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import com.micaftic.morpher.core.api.network.state.CloudErrorCode;

import java.net.URI;
import java.net.InetSocketAddress;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CloudHttpClientTest {

    @TempDir Path directory;

    @Test
    void uploadsUnicodeMetadataWithoutChangingAssetIdentityOrBytes() throws Exception {
        byte[] content = {0, 1, 2, 13, 10, (byte) 255, 42};
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        Path source = Files.write(directory.resolve("芙宁娜v3.14日语配音.ysm"), content);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<CapturedUpload> capture = new AtomicReference<>();
        server.createContext("/v1/assets", exchange -> {
            capture.set(new CapturedUpload(
                    exchange.getRequestHeaders().getFirst("X-Asset-Id"),
                    exchange.getRequestHeaders().getFirst("X-Asset-Name"),
                    exchange.getRequestHeaders().getFirst("X-Asset-Metadata-Encoding"),
                    exchange.getRequestHeaders().getFirst("X-Asset-Sha256"),
                    exchange.getRequestHeaders().getFirst("X-Asset-Visibility"),
                    exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        server.start();
        try (HttpClient delegate = HttpClient.newHttpClient()) {
            CloudHttpClient client = new CloudHttpClient(INSTANCE,
                    new LoopbackClient(delegate, URI.create("http://127.0.0.1:" + server.getAddress().getPort())));
            for (String assetId : new String[]{"芙宁娜v3.14日语配音", "ascii-model", "模型 100%+测试"}) {
                for (boolean stream : new boolean[]{false, true}) {
                    String name = assetId + ".ysm";
                    AtomicLong sent = new AtomicLong();
                    HttpResponse<byte[]> response = stream
                            ? client.uploadAsset(source, content.length, assetId, name, "ysm", sha, "request-test", "PUBLIC",
                                    (bytes, total) -> sent.set(bytes), () -> false).get(10, TimeUnit.SECONDS)
                            : client.uploadAsset(content, assetId, name, "ysm", sha, "request-test", "PUBLIC").get(10, TimeUnit.SECONDS);
                    assertEquals(201, response.statusCode());
                    CapturedUpload received = capture.get();
                    assertEquals("utf-8-percent", received.encoding());
                    assertTrue(received.assetId().chars().allMatch(c -> c < 128));
                    assertTrue(received.name().chars().allMatch(c -> c < 128));
                    assertEquals(assetId, URLDecoder.decode(received.assetId(), StandardCharsets.UTF_8));
                    assertEquals(name, URLDecoder.decode(received.name(), StandardCharsets.UTF_8));
                    assertEquals(sha, received.sha());
                    assertEquals("PUBLIC", received.visibility());
                    assertArrayEquals(content, received.content());
                    if (stream) assertEquals(content.length, sent.get());
                    capture.set(null);
                }
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void duplicateRegistrationPreservesAccountExistsCodeAndExplanation() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/auth/register", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"code\":\"ACCOUNT_EXISTS\",\"message\":\"Cloud account already exists; sign in instead\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(409, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try (HttpClient delegate = HttpClient.newHttpClient()) {
            var client = new CloudHttpClient(INSTANCE, new LoopbackClient(delegate, URI.create("http://127.0.0.1:" + server.getAddress().getPort())));
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> client.postJson("/v1/auth/register", "{}").get(10, TimeUnit.SECONDS));
            var cloud = (CloudHttpException) failure.getCause();
            assertEquals(409, cloud.statusCode());
            assertEquals("ACCOUNT_EXISTS", cloud.errorCode().name());
            assertEquals("Cloud account already exists; sign in instead", cloud.getMessage());
        } finally { server.stop(0); }
    }

    private record CapturedUpload(String assetId, String name, String encoding, String sha, String visibility, byte[] content) {}

    @Test
    void uploadPreservesServerErrorCodeAndExplanationForBothBodies() throws Exception {
        byte[] content = "model".getBytes(StandardCharsets.UTF_8);
        Path source = Files.createTempFile("cloud-error-", ".ysm");
        Files.write(source, content);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/assets", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] error = "{\"code\":\"ASSET_ACCESS_DENIED\",\"message\":\"only the asset owner may upload a new revision\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(403, error.length);
            exchange.getResponseBody().write(error);
            exchange.close();
        });
        server.start();
        try (HttpClient delegate = HttpClient.newHttpClient()) {
            CloudAssetClient assets = new CloudAssetClient(new CloudHttpClient(INSTANCE,
                    new LoopbackClient(delegate, URI.create("http://127.0.0.1:" + server.getAddress().getPort()))));
            for (boolean stream : new boolean[]{false, true}) {
                var future = stream ? assets.upload(source, "model", "model.ysm", "ysm", "a".repeat(64),
                        "request-error", (sent, total) -> {}, () -> false)
                        : assets.upload(content, "model", "model.ysm", "ysm", "a".repeat(64));
                var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS));
                CloudHttpException http = (CloudHttpException) failure.getCause();
                assertEquals(403, http.statusCode());
                assertEquals(CloudErrorCode.ASSET_ACCESS_DENIED, http.errorCode());
                assertEquals("only the asset owner may upload a new revision", http.getMessage());
            }
        } finally { server.stop(0); Files.deleteIfExists(source); }
    }

    @Test
    void visibilityUpdateUsesEncodedAssetPathAndJsonWithoutUploadingBytes() throws Exception {
        String assetId = "模型v3.14 100%+";
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> path = new AtomicReference<>(), method = new AtomicReference<>(), body = new AtomicReference<>();
        server.createContext("/v1/assets", exchange -> {
            path.set(exchange.getRequestURI().getRawPath());
            method.set(exchange.getRequestMethod());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String visibility = com.google.gson.JsonParser.parseString(body.get()).getAsJsonObject().get("visibility").getAsString();
            var response = new com.google.gson.JsonObject();
            response.addProperty("asset_id", assetId);
            response.addProperty("revision", 7);
            response.addProperty("raw_sha256", "a".repeat(64));
            response.addProperty("name", "model.ysm");
            response.addProperty("format", "ysm");
            response.addProperty("byte_length", 123);
            response.addProperty("visibility", visibility);
            byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try (HttpClient delegate = HttpClient.newHttpClient()) {
            CloudAssetClient assets = new CloudAssetClient(new CloudHttpClient(INSTANCE,
                    new LoopbackClient(delegate, URI.create("http://127.0.0.1:" + server.getAddress().getPort()))));
            for (String visibility : new String[]{"PUBLIC", "PRIVATE"}) {
                var summary = assets.setVisibility(assetId, visibility).get(10, TimeUnit.SECONDS);
                assertEquals("PUT", method.get());
                assertEquals("/v1/assets/%E6%A8%A1%E5%9E%8Bv3%2E14%20100%25%2B/visibility", path.get());
                assertEquals(1, com.google.gson.JsonParser.parseString(body.get()).getAsJsonObject().size());
                assertEquals(assetId, summary.ref().assetId());
                assertEquals(7, summary.ref().revision());
                assertEquals(visibility, summary.visibility());
            }
            assertThrows(IllegalArgumentException.class, () -> assets.setVisibility(assetId, "unlisted"));
        } finally { server.stop(0); }
    }

    /** Keeps production HTTPS origin validation while capturing real requests on an isolated HTTP server. */
    private static final class LoopbackClient extends HttpClient {
        private final HttpClient delegate;
        private final URI origin;
        LoopbackClient(HttpClient delegate, URI origin) { this.delegate = delegate; this.origin = origin; }
        @Override public Optional<CookieHandler> cookieHandler() { return delegate.cookieHandler(); }
        @Override public Optional<Duration> connectTimeout() { return delegate.connectTimeout(); }
        @Override public Redirect followRedirects() { return delegate.followRedirects(); }
        @Override public Optional<ProxySelector> proxy() { return delegate.proxy(); }
        @Override public SSLContext sslContext() { return delegate.sslContext(); }
        @Override public SSLParameters sslParameters() { return delegate.sslParameters(); }
        @Override public Optional<Authenticator> authenticator() { return delegate.authenticator(); }
        @Override public Version version() { return delegate.version(); }
        @Override public Optional<Executor> executor() { return delegate.executor(); }
        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException();
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(origin.resolve(request.uri().getRawPath()));
            request.headers().map().forEach((key, values) -> values.forEach(value -> builder.header(key, value)));
            builder.method(request.method(), request.bodyPublisher().orElseThrow());
            return delegate.sendAsync(builder.build(), handler);
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushHandler) { return sendAsync(request, handler); }
    }

    private static final CloudInstanceConfig INSTANCE = CloudInstanceConfig.v1(
            "local-dev", URI.create("https://cloud.example.test"));

    @Test
    void acceptsOnlyMatchingTrustedInstanceAndLimits() {
        CloudInstanceInfo info = CloudHttpClient.parseInstanceResponse(INSTANCE, """
                {"instance_id":"local-dev","origin":"https://cloud.example.test","protocol":"spm.cloud.v1",
                 "limits":{"max_message_bytes":65536,"max_asset_bytes":1024}}
                """);

        assertEquals(INSTANCE, info.config());
        assertEquals(65_536, info.maxMessageBytes());
        assertEquals(1024, info.maxAssetBytes());
        assertEquals(45, info.heartbeatTtlSeconds());
        assertEquals(false, info.playerMotionSupported());
        CloudInstanceInfo upgraded = CloudHttpClient.parseInstanceResponse(INSTANCE, """
                {"instance_id":"local-dev","origin":"https://cloud.example.test","protocol":"spm.cloud.v1",
                 "capabilities":["future_capability","player_motion_v1"]}
                """);
        assertTrue(upgraded.playerMotionSupported());
    }

    @Test
    void rejectsOriginOrProtocolSubstitution() {
        CloudHttpException originFailure = assertThrows(CloudHttpException.class, () ->
                CloudHttpClient.parseInstanceResponse(INSTANCE,
                        "{\"instance_id\":\"local-dev\",\"origin\":\"https://evil.example\",\"protocol\":\"spm.cloud.v1\"}"));
        assertEquals(CloudErrorCode.INSTANCE_MISMATCH, originFailure.errorCode());

        CloudHttpException protocolFailure = assertThrows(CloudHttpException.class, () ->
                CloudHttpClient.parseInstanceResponse(INSTANCE,
                        "{\"instance_id\":\"local-dev\",\"origin\":\"https://cloud.example.test\",\"protocol\":\"spm.cloud.v2\"}"));
        assertEquals(CloudErrorCode.PROTOCOL_UNSUPPORTED, protocolFailure.errorCode());
    }
}
