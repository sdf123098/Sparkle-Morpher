package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CloudAssetDisplayDownloadTest {
    @TempDir Path directory;

    @Test void exactRevisionFormatWinsAndOldBackendFallbackRequiresExactMetadata() throws Exception {
        byte[] bytes = new byte[]{1, 2, 3};
        var format = new AtomicReference<String>("zip");
        var status = new java.util.concurrent.atomic.AtomicInteger(200);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var requests = new java.util.concurrent.CopyOnWriteArrayList<String>();
        server.createContext("/v1/assets", exchange -> {
            requests.add(exchange.getRequestURI().getPath() + " " + exchange.getRequestHeaders().getFirst("Authorization"));
            if (format.get() != null) exchange.getResponseHeaders().set("X-Asset-Format", format.get());
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try (HttpClient delegate = HttpClient.newHttpClient()) {
            var instance = CloudInstanceConfig.v1("official", URI.create("https://cloud.example.test"));
            var http = new CloudHttpClient(instance, new CloudPlayerPresenceClientTest.LoopbackClient(delegate,
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort())), "isolated-test-session");
            var client = new CloudAssetClient(http);
            var cache = new CloudAssetCache();
            var ref = new CloudAssetRef("historical", 2, CloudAssetCache.sha256(bytes));
            var asset = cache.downloadForDisplay(client, ref, directory, "ysm").get(10, TimeUnit.SECONDS);
            assertEquals("zip", asset.format(), "latest catalog format must not replace the exact download format");
            assertArrayEquals(bytes, Files.readAllBytes(asset.path()));
            format.set(null);
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> cache.downloadForDisplay(client, ref, directory, null).get(10, TimeUnit.SECONDS));
            assertEquals("bbmodel", cache.downloadForDisplay(client, ref, directory, "bbmodel").get(10, TimeUnit.SECONDS).format());
            status.set(403);
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> cache.downloadForDisplay(client, ref, directory, "bbmodel").get(10, TimeUnit.SECONDS),
                    "resident cache must not bypass current authorization");
            assertEquals(4, requests.size());
            assertTrue(requests.stream().allMatch(request -> request.equals(
                    "/v1/assets/historical/revisions/2/content Bearer isolated-test-session")));
        } finally { server.stop(0); }
    }
}
