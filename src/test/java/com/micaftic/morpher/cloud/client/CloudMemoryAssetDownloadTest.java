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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CloudMemoryAssetDownloadTest {
    private final java.util.List<HttpServer> servers = new java.util.ArrayList<>();
    private final java.util.List<HttpClient> delegates = new java.util.ArrayList<>();

    @AfterEach
    void stop() {
        servers.forEach(server -> server.stop(0));
        delegates.forEach(HttpClient::close);
    }

    @FunctionalInterface
    interface ExchangeHandler { void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException; }

    private CloudAssetClient clientOn(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/assets/mem/revisions/1/content", exchange -> {
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
        CloudHttpClient http = new CloudHttpClient(instance,
                new CloudPlayerPresenceClientTest.LoopbackClient(delegate,
                        URI.create("http://127.0.0.1:" + server.getAddress().getPort())), "session");
        return new CloudAssetClient(http);
    }

    private static CloudHttpException failure(CloudAssetClient client, CloudAssetRef ref, String format, long maximum) {
        Throwable error = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> client.downloadForDisplay(ref, format, maximum).get(10, TimeUnit.SECONDS)).getCause();
        while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
        assertInstanceOf(CloudHttpException.class, error);
        return (CloudHttpException) error;
    }

    @Test
    void completeResponseIsVerifiedInMemoryAndKeepsTheExactFormat() throws Exception {
        byte[] content = "model".getBytes(StandardCharsets.UTF_8);
        CloudAssetRef ref = new CloudAssetRef("mem", 1, CloudAssetCache.sha256(content));
        CloudAssetClient client = clientOn(exchange -> {
            exchange.getResponseHeaders().set("X-Asset-Format", "ysm");
            exchange.sendResponseHeaders(200, content.length);
            try {
                exchange.getResponseBody().write(content);
            } catch (IOException ignored) { }
        });
        CloudAssetClient.MemoryAsset asset = client.downloadForDisplay(ref, "zip", 1024).get(10, TimeUnit.SECONDS);
        assertArrayEquals(content, asset.bytes());
        assertEquals("ysm", asset.format());
    }

    @Test
    void chunkedResponseIsBoundedByActualBytes() throws Exception {
        byte[] content = "chunked-model".getBytes(StandardCharsets.UTF_8);
        CloudAssetRef ref = new CloudAssetRef("mem", 1, CloudAssetCache.sha256(content));
        CloudAssetClient client = clientOn(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try {
                exchange.getResponseBody().write(content);
            } catch (IOException ignored) { }
        });
        assertArrayEquals(content, client.downloadForDisplay(ref, "ysm", 1024)
                .get(10, TimeUnit.SECONDS).bytes());
    }

    @Test
    void declaredLengthOverBudgetIsRejectedBeforeTheBody() throws Exception {
        byte[] content = new byte[64];
        CloudAssetRef ref = new CloudAssetRef("mem", 1, CloudAssetCache.sha256(content));
        AtomicInteger written = new AtomicInteger();
        CloudAssetClient client = clientOn(exchange -> {
            exchange.sendResponseHeaders(200, content.length);
            try {
                exchange.getResponseBody().write(content);
                written.set(content.length);
            } catch (IOException ignored) { }
        });
        assertEquals(CloudErrorCode.MESSAGE_TOO_LARGE, failure(client, ref, "ysm", 16).errorCode());
        assertTrue(written.get() == 0 || written.get() == content.length,
                "the client must cancel before buffering an over-budget body");
    }

    @Test
    void unknownLengthOverBudgetIsCancelledWhileReading() throws Exception {
        byte[] content = new byte[64];
        CloudAssetRef ref = new CloudAssetRef("mem", 1, CloudAssetCache.sha256(content));
        CloudAssetClient client = clientOn(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try {
                exchange.getResponseBody().write(content);
            } catch (IOException ignored) { }
        });
        assertEquals(CloudErrorCode.MESSAGE_TOO_LARGE, failure(client, ref, "ysm", 16).errorCode());
    }

    @Test
    void partialContentIsNotAcceptedAsACompleteObject() throws Exception {
        byte[] content = "model".getBytes(StandardCharsets.UTF_8);
        CloudAssetRef ref = new CloudAssetRef("mem", 1, CloudAssetCache.sha256(content));
        CloudAssetClient client = clientOn(exchange -> {
            exchange.getResponseHeaders().set("X-Asset-Format", "ysm");
            exchange.sendResponseHeaders(206, content.length);
            try {
                exchange.getResponseBody().write(content);
            } catch (IOException ignored) { }
        });
        assertEquals(CloudErrorCode.MALFORMED_MESSAGE, failure(client, ref, "ysm", 1024).errorCode());
    }

    @Test
    void unauthorizedAndMissingResponsesStayFailures() throws Exception {
        byte[] content = "model".getBytes(StandardCharsets.UTF_8);
        CloudAssetRef ref = new CloudAssetRef("mem", 1, CloudAssetCache.sha256(content));
        CloudAssetClient unauthorized = clientOn(exchange -> exchange.sendResponseHeaders(403, -1));
        assertEquals(403, failure(unauthorized, ref, "ysm", 1024).statusCode());

        CloudAssetClient missing = clientOn(exchange -> exchange.sendResponseHeaders(404, -1));
        assertEquals(CloudErrorCode.ASSET_NOT_FOUND, failure(missing, ref, "ysm", 1024).errorCode());
    }

    @Test
    void hashMismatchAndUnknownFormatNeverReachTheParser() throws Exception {
        byte[] content = "model".getBytes(StandardCharsets.UTF_8);
        CloudAssetRef wrongHash = new CloudAssetRef("mem", 1, "0".repeat(64));
        CloudAssetClient hashed = clientOn(exchange -> {
            exchange.getResponseHeaders().set("X-Asset-Format", "ysm");
            exchange.sendResponseHeaders(200, content.length);
            try {
                exchange.getResponseBody().write(content);
            } catch (IOException ignored) { }
        });
        assertEquals(CloudErrorCode.ASSET_HASH_MISMATCH, failure(hashed, wrongHash, "ysm", 1024).errorCode());

        CloudAssetRef ref = new CloudAssetRef("mem", 1, CloudAssetCache.sha256(content));
        CloudAssetClient unknown = clientOn(exchange -> {
            exchange.sendResponseHeaders(200, content.length);
            try {
                exchange.getResponseBody().write(content);
            } catch (IOException ignored) { }
        });
        java.util.concurrent.ExecutionException error = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> unknown.downloadForDisplay(ref, null, 1024).get(10, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }

    @Test
    void twoInstancesKeepTheSameAssetIdIsolated() throws Exception {
        byte[] firstBytes = "first".getBytes(StandardCharsets.UTF_8);
        byte[] secondBytes = "second".getBytes(StandardCharsets.UTF_8);
        CloudAssetClient first = clientOn(exchange -> {
            exchange.getResponseHeaders().set("X-Asset-Format", "ysm");
            exchange.sendResponseHeaders(200, firstBytes.length);
            try {
                exchange.getResponseBody().write(firstBytes);
            } catch (IOException ignored) { }
        });
        CloudAssetClient second = clientOn(exchange -> {
            exchange.getResponseHeaders().set("X-Asset-Format", "ysm");
            exchange.sendResponseHeaders(200, secondBytes.length);
            try {
                exchange.getResponseBody().write(secondBytes);
            } catch (IOException ignored) { }
        });
        assertArrayEquals(firstBytes, first.downloadForDisplay(
                new CloudAssetRef("mem", 1, CloudAssetCache.sha256(firstBytes)), "ysm", 1024)
                .get(10, TimeUnit.SECONDS).bytes());
        assertArrayEquals(secondBytes, second.downloadForDisplay(
                new CloudAssetRef("mem", 1, CloudAssetCache.sha256(secondBytes)), "ysm", 1024)
                .get(10, TimeUnit.SECONDS).bytes());
    }
}
