package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The reopened-model promise: one network fetch, then silence.
 *
 * <p>Every case here drives the real transport against a loopback server, so the
 * request counts are exactly the ones a player would cause by reopening a model
 * twice. A cached blob is still revalidated once per runtime, and a revoked
 * revision must lose its bytes instead of replaying them forever.</p>
 */
class CloudDiskCacheDownloadTest {

    private final List<HttpServer> servers = new ArrayList<>();
    private final List<HttpClient> delegates = new ArrayList<>();
    private final List<Path> roots = new ArrayList<>();

    @AfterEach
    void stop() {
        servers.forEach(server -> server.stop(0));
        delegates.forEach(HttpClient::close);
        roots.forEach(path -> deleteTree(path));
    }

    /** Counts content fetches and access checks separately; both live on the same content URL. */
    private record Probe(AtomicInteger content, AtomicInteger access, AtomicReference<String> lastIfNoneMatch) {}

    private record Fixture(CloudAssetClient client, CloudDiskCache cache, Probe probe, CloudHttpClient http) {}

    private Fixture fixture(byte[] content, boolean grantAccess) throws IOException {
        String sha = CloudAssetCache.sha256(content);
        Probe probe = new Probe(new AtomicInteger(), new AtomicInteger(), new AtomicReference<>());
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/assets/", exchange -> {
            try {
                if (exchange.getRequestHeaders().getFirst("If-None-Match") != null) {
                    probe.access().incrementAndGet();
                    probe.lastIfNoneMatch().set(exchange.getRequestHeaders().getFirst("If-None-Match"));
                    respondAccess(exchange, sha, grantAccess);
                } else {
                    probe.content().incrementAndGet();
                    respondContent(exchange, content);
                }
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
        Path root = Files.createTempDirectory("sm-disk-download-");
        roots.add(root);
        CloudDiskCache cache = new CloudDiskCache(root, CloudDiskCache.MIN_MAX_BYTES);
        return new Fixture(new CloudAssetClient(http, cache), cache, probe, http);
    }

    private static void respondContent(HttpExchange exchange, byte[] content) throws IOException {
        exchange.getResponseHeaders().set("X-Asset-Format", "ysm");
        exchange.sendResponseHeaders(200, content.length);
        exchange.getResponseBody().write(content);
    }

    private static void respondAccess(HttpExchange exchange, String sha, boolean granted) throws IOException {
        if (!granted) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }
        exchange.getResponseHeaders().set("ETag", "\"" + sha + "\"");
        exchange.sendResponseHeaders(304, -1);
    }

    @Test
    void aReopenedModelIsServedFromDiskWithNoSecondContentRequest() throws Exception {
        byte[] content = "reopen-me".getBytes(StandardCharsets.UTF_8);
        Fixture fixture = fixture(content, true);
        CloudAssetRef ref = new CloudAssetRef("mem", 1, CloudAssetCache.sha256(content));

        CloudAssetClient.MemoryAsset first = fixture.client()
                .downloadForDisplay(ref, "ysm", 1024).get(10, TimeUnit.SECONDS);
        assertArrayEquals(content, first.bytes());
        assertEquals("ysm", first.format());

        CloudAssetClient.MemoryAsset second = fixture.client()
                .downloadForDisplay(ref, "ysm", 1024).get(10, TimeUnit.SECONDS);
        assertArrayEquals(content, second.bytes());
        assertEquals("ysm", second.format());

        assertEquals(1, fixture.probe().content().get(), "reopening must not fetch the model again");
        assertEquals(1, fixture.probe().access().get(), "the cached blob is revalidated exactly once");
        assertEquals("\"" + ref.rawSha256() + "\"", fixture.probe().lastIfNoneMatch().get());
    }

    @Test
    void theCachedBytesLiveInTheStoreAndNowhereElse() throws Exception {
        byte[] content = "cached-object".getBytes(StandardCharsets.UTF_8);
        Fixture fixture = fixture(content, true);
        CloudAssetRef ref = new CloudAssetRef("mem", 1, CloudAssetCache.sha256(content));
        fixture.client().downloadForDisplay(ref, "ysm", 1024).get(10, TimeUnit.SECONDS);

        byte[] stored = fixture.cache().readObject(ref.rawSha256());
        assertNotNull(stored, "the store must hold the verified object by its digest");
        assertArrayEquals(content, stored);
        assertEquals(content.length, fixture.cache().residentBytes());
    }

    @Test
    void aRevokedRevisionLosesItsCachedBytesInsteadOfReplayingThem() throws Exception {
        byte[] content = "revoked".getBytes(StandardCharsets.UTF_8);
        Fixture fixture = fixture(content, false);
        CloudAssetRef ref = new CloudAssetRef("mem", 1, CloudAssetCache.sha256(content));

        // Seed the store as if the model had been opened while access was still granted.
        fixture.cache().writeObject(ref.rawSha256(), content);

        Throwable error = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> fixture.client().downloadForDisplay(ref, "ysm", 1024).get(10, TimeUnit.SECONDS)).getCause();
        while (error.getCause() != null) error = error.getCause();
        assertInstanceOf(SecurityException.class, error);
        assertEquals(0, fixture.probe().content().get(), "a refused revision must never be refetched");
        assertNull(fixture.cache().readObject(ref.rawSha256()), "refused bytes must not stay on disk");
    }

    @Test
    void aCachedBlobOverBudgetIsDroppedAndRefetched() throws Exception {
        byte[] content = "budget-bound".getBytes(StandardCharsets.UTF_8);
        Fixture fixture = fixture(content, true);
        CloudAssetRef ref = new CloudAssetRef("mem", 1, CloudAssetCache.sha256(content));
        fixture.cache().writeObject(ref.rawSha256(), content);

        // The budget also bounds the refetch, so the call fails — the point is that it went to the
        // network instead of serving a blob that does not fit the caller's budget.
        Throwable error = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> fixture.client().downloadForDisplay(ref, "ysm", 4).get(10, TimeUnit.SECONDS)).getCause();
        while (error.getCause() != null) error = error.getCause();
        assertInstanceOf(CloudHttpException.class, error);
        assertEquals(1, fixture.probe().content().get(), "an over-budget blob is not served from disk");
        assertNull(fixture.cache().readObject(ref.rawSha256()), "the over-budget blob is dropped");
    }

    @Test
    void aCorruptCachedBlobIsDroppedAndRefetched() throws Exception {
        byte[] content = "self-verifying".getBytes(StandardCharsets.UTF_8);
        Fixture fixture = fixture(content, true);
        CloudAssetRef ref = new CloudAssetRef("mem", 1, CloudAssetCache.sha256(content));
        // Same size, different bytes: only the digest can tell these apart.
        byte[] corrupt = new byte[content.length];
        java.util.Arrays.fill(corrupt, (byte) 'x');
        fixture.cache().writeObject(ref.rawSha256(), corrupt);

        CloudAssetClient.MemoryAsset asset = fixture.client()
                .downloadForDisplay(ref, "ysm", 1024).get(10, TimeUnit.SECONDS);
        assertArrayEquals(content, asset.bytes());
        assertEquals(1, fixture.probe().content().get());
        assertArrayEquals(content, fixture.cache().readObject(ref.rawSha256()),
                "the corrupt blob is replaced by the verified one");
    }

    @Test
    void anUnknownCatalogFormatFallsBackToTheNetworkInsteadOfGuessing() throws Exception {
        byte[] content = "no-format".getBytes(StandardCharsets.UTF_8);
        Fixture fixture = fixture(content, true);
        CloudAssetRef ref = new CloudAssetRef("mem", 1, CloudAssetCache.sha256(content));
        fixture.cache().writeObject(ref.rawSha256(), content);

        fixture.client().downloadForDisplay(ref, null, 1024).get(10, TimeUnit.SECONDS);
        assertEquals(1, fixture.probe().content().get(),
                "model bytes cannot reveal their format, so a cache hit requires a known format");
    }

    @Test
    void aClientWithoutAStoreKeepsTheMemoryOnlyBehaviour() throws Exception {
        byte[] content = "no-store".getBytes(StandardCharsets.UTF_8);
        Fixture fixture = fixture(content, true);
        CloudAssetRef ref = new CloudAssetRef("mem", 1, CloudAssetCache.sha256(content));
        // Seeded on disk, but this client was built without a store, so it cannot see it.
        fixture.cache().writeObject(ref.rawSha256(), content);
        CloudAssetClient memoryOnly = new CloudAssetClient(fixture.http());
        assertFalse(memoryOnly.cachesDownloads(), "the single-argument constructor stays memory-only");

        memoryOnly.downloadForDisplay(ref, "ysm", 1024).get(10, TimeUnit.SECONDS);
        assertEquals(1, fixture.probe().content().get(), "a store-less client always fetches");
        assertEquals(0, fixture.probe().access().get(), "a store-less client has nothing to revalidate");
    }

    private static void deleteTree(Path root) {
        if (root == null) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }
}