package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import com.micaftic.morpher.core.api.network.state.CloudErrorCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The card image transport must stay anonymous and confined to the thumbnail route. */
class CloudPublicThumbnailTest {
    private final List<HttpServer> servers = new CopyOnWriteArrayList<>();
    private final List<HttpClient> delegates = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();

    @AfterEach
    void stop() {
        servers.forEach(server -> server.stop(0));
        delegates.forEach(HttpClient::close);
    }

    @FunctionalInterface
    interface ExchangeHandler { void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException; }

    private CloudHttpClient clientOn(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getRawPath());
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            authorizations.add(authorization == null ? "" : authorization);
            try {
                handler.handle(exchange);
            } catch (IOException error) {
                throw new java.io.UncheckedIOException(error);
            } finally {
                exchange.close();
            }
        });
        server.start();
        servers.add(server);
        HttpClient delegate = HttpClient.newHttpClient();
        delegates.add(delegate);
        CloudInstanceConfig instance = CloudInstanceConfig.v1("official", URI.create("https://cloud.example.test"));
        return new CloudHttpClient(instance, new CloudPlayerPresenceClientTest.LoopbackClient(delegate,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort())), "session-token");
    }

    @Test
    void cardImageIsFetchedWithoutTheBearerCredential() throws Exception {
        byte[] image = "png".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        CloudHttpClient client = clientOn(exchange -> {
            exchange.sendResponseHeaders(200, image.length);
            try {
                exchange.getResponseBody().write(image);
            } catch (IOException ignored) { }
        });
        HttpResponse<byte[]> response = client.getPublicThumbnail(
                "/v1/public/assets/model_01/revisions/2/thumbnail", 1024).get(10, TimeUnit.SECONDS);
        assertEquals(200, response.statusCode());
        assertArrayEquals(image, response.body());
        assertEquals(List.of("/v1/public/assets/model_01/revisions/2/thumbnail"), paths);
        assertEquals(List.of(""), authorizations, "the anonymous route must not receive credentials");
    }

    @Test
    void theAuthenticatedRouteStillSendsTheCredential() throws Exception {
        CloudHttpClient client = clientOn(exchange -> exchange.sendResponseHeaders(200, -1));
        client.getBoundedBytes("/v1/assets/model_01/revisions/2/content", 1024).get(10, TimeUnit.SECONDS);
        assertEquals(List.of("Bearer session-token"), authorizations);
    }

    @Test
    void missingImageIsAPlainStatusWithoutCredentialOrRetry() throws Exception {
        CloudHttpClient client = clientOn(exchange -> exchange.sendResponseHeaders(404, -1));
        HttpResponse<byte[]> response = client.getPublicThumbnail(
                "/v1/public/assets/model_01/revisions/2/thumbnail", 1024).get(10, TimeUnit.SECONDS);
        assertEquals(404, response.statusCode());
        assertEquals(1, paths.size());
        assertEquals("", authorizations.getFirst());
    }

    @Test
    void overBudgetImageIsRejectedBeforeItIsBuffered() throws Exception {
        AtomicInteger written = new AtomicInteger();
        CloudHttpClient client = clientOn(exchange -> {
            byte[] oversized = new byte[256];
            exchange.sendResponseHeaders(200, oversized.length);
            try {
                exchange.getResponseBody().write(oversized);
                written.set(oversized.length);
            } catch (IOException ignored) { }
        });
        Throwable error = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> client.getPublicThumbnail("/v1/public/assets/model_01/revisions/2/thumbnail", 64)
                        .get(10, TimeUnit.SECONDS)).getCause();
        while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
        assertInstanceOf(CloudHttpException.class, error);
        assertEquals(CloudErrorCode.MESSAGE_TOO_LARGE, ((CloudHttpException) error).errorCode());
    }

    @Test
    void transportRefusesEveryOtherRoute() {
        CloudHttpClient client;
        try {
            client = clientOn(exchange -> exchange.sendResponseHeaders(200, -1));
        } catch (IOException unavailable) {
            throw new AssertionError(unavailable);
        }
        for (String rejected : new String[]{
                null, "", "/v1/assets/model_01/revisions/2/content",
                "/v1/public/assets/model_01/revisions/2/content",
                "/v1/public/assets/model_01/revisions/2/thumbnail?x=1",
                "https://cloud.example.test/v1/public/assets/m/revisions/1/thumbnail",
                "/v1/public/assets/../thumbnail",
                "/v1/public/assets/m/revisions/1/thumbnail\n"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> client.getPublicThumbnail(rejected, 1024), String.valueOf(rejected));
        }
        assertTrue(paths.isEmpty(), "a rejected path must never reach the network");
        assertNull(null);
    }

    private static void assertInstanceOf(Class<?> expected, Object actual) {
        assertTrue(expected.isInstance(actual), "expected " + expected.getSimpleName() + " but was " + actual);
    }
}
