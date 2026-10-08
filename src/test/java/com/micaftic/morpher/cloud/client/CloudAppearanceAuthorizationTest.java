package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class CloudAppearanceAuthorizationTest {
    @Test void rechecksTargetAndExactCachedAssetIncludingRevocationAndOldServer() throws Exception {
        String sha = "a".repeat(64);
        var assetStatus = new AtomicInteger(304);
        var targetStatus = new AtomicInteger(200);
        var targetRevision = new AtomicInteger(7);
        var etag = new AtomicReference<String>("\"" + sha + "\"");
        var paths = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1", exchange -> {
            String path = exchange.getRequestURI().getPath();
            paths.add(path);
            assertEquals("Bearer isolated-test-session", exchange.getRequestHeaders().getFirst("Authorization"));
            if (path.startsWith("/v1/targets")) {
                byte[] body = ("{\"target_id\":\"bound-maid\",\"revision\":" + targetRevision.get()
                        + ",\"asset_id\":\"old-model\",\"asset_revision\":2,\"raw_sha256\":\"" + sha
                        + "\",\"texture_id\":\"default\",\"scale\":null,\"disabled\":false}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(targetStatus.get(), body.length);
                exchange.getResponseBody().write(body);
            } else {
                assertEquals("/v1/assets/old-model/revisions/2/content", path);
                assertEquals("\"" + sha + "\"", exchange.getRequestHeaders().getFirst("If-None-Match"));
                if (etag.get() != null) exchange.getResponseHeaders().set("ETag", etag.get());
                exchange.sendResponseHeaders(assetStatus.get(), -1);
            }
            exchange.close();
        });
        server.start();
        try (HttpClient delegate = HttpClient.newHttpClient()) {
            var config = CloudInstanceConfig.v1("official", URI.create("https://cloud.example.test"));
            var http = new CloudHttpClient(config, new CloudPlayerPresenceClientTest.LoopbackClient(delegate,
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort())), "isolated-test-session");
            var scopes = new CloudScopeClient(http);
            var assets = new CloudAssetClient(http);
            var expected = new CloudScopeClient.CloudAppearance("bound-maid", 7, "old-model", 2L, sha, "default", null, false);
            var invalid = new CloudScopeClient.CloudAppearance("../invalid", 7, "old-model", 2L, sha, "default", null, false);
            assertFalse(CloudAppearanceAuthorization.check(scopes, assets, invalid).get(10, TimeUnit.SECONDS));
            assertTrue(paths.isEmpty(), "invalid target must not start HTTP or throw on the client thread");
            assertTrue(CloudAppearanceAuthorization.check(scopes, assets, expected).get(10, TimeUnit.SECONDS));
            assetStatus.set(403);
            assertFalse(CloudAppearanceAuthorization.check(scopes, assets, expected).get(10, TimeUnit.SECONDS));
            assetStatus.set(200);
            assertTrue(CloudAppearanceAuthorization.check(scopes, assets, expected).get(10, TimeUnit.SECONDS), "older server can return 200 with exact ETag");
            etag.set("\"wrong-revision\"");
            assertFalse(CloudAppearanceAuthorization.check(scopes, assets, expected).get(10, TimeUnit.SECONDS));
            etag.set(null);
            assertFalse(CloudAppearanceAuthorization.check(scopes, assets, expected).get(10, TimeUnit.SECONDS));
            int before = paths.size();
            targetRevision.set(8);
            assertFalse(CloudAppearanceAuthorization.check(scopes, assets, expected).get(10, TimeUnit.SECONDS));
            assertEquals(before + 1, paths.size(), "changed target cannot authorize stale asset");
            targetStatus.set(403);
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> CloudAppearanceAuthorization.check(scopes, assets, expected).get(10, TimeUnit.SECONDS));
        } finally { server.stop(0); }
    }
}
