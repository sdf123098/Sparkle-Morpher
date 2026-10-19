package com.micaftic.morpher.cloud.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.micaftic.morpher.cloud.CloudInstanceConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CloudPublicCatalogTest {
    @Test
    void preservesNativePublicBrowsingAndUnicodeSearch() throws Exception {
        List<String> requests = new java.util.concurrent.CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/assets", exchange -> {
            requests.add(exchange.getRequestURI().getRawQuery());
            JsonObject page = new JsonObject();
            JsonArray entries = new JsonArray();
            entries.add(asset(1, true));
            page.add("entries", entries);
            page.addProperty("has_more", false);
            byte[] bytes = page.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try (HttpClient delegate = HttpClient.newHttpClient()) {
            CloudAssetClient client = client(delegate, server);
            assertEquals(1, client.listPage("public", "", null, 24).get(10, TimeUnit.SECONDS).entries().size());
            assertEquals(1, client.listPage("public", "模型 100%+", "前页", 12).get(10, TimeUnit.SECONDS).entries().size());
            assertEquals(List.of("scope=public&limit=24", "scope=public&limit=12&q=%E6%A8%A1%E5%9E%8B%20100%25%2B&after=%E5%89%8D%E9%A1%B5"), requests);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotMaskAuthenticationFailuresAsEmptyPublicPages() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/assets", exchange -> {
            requests.incrementAndGet();
            byte[] bytes = "{\"code\":\"UNAUTHENTICATED\",\"message\":\"Log in again\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try (HttpClient delegate = HttpClient.newHttpClient()) {
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> client(delegate, server).listPage("public", "", null, 24).get(10, TimeUnit.SECONDS));
            Throwable cause = failure.getCause();
            while (cause instanceof java.util.concurrent.CompletionException) cause = cause.getCause();
            assertEquals(401, ((CloudHttpException) cause).statusCode());
            assertEquals("Log in again", cause.getMessage());
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void browsesAllPublicModelsOnSearchOnlyServersWithoutShowingPrivateRowsOrLosingPages() throws Exception {
        AtomicInteger rejected = new AtomicInteger();
        List<String> requests = new java.util.concurrent.CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // The old deployment allows accessible listings (including all PUBLIC assets),
        // but rejects public browsing without a search term.
        List<Boolean> publicRows = List.of(false, false, false, true, false, true, true, false, true, false);
        server.createContext("/v1/assets", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            requests.add(query);
            Map<String, String> parameters = java.util.Arrays.stream(query.split("&"))
                    .map(pair -> pair.split("=", 2))
                    .collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
            byte[] bytes;
            int status;
            if (parameters.get("scope").equals("public") && !parameters.containsKey("q")) {
                rejected.incrementAndGet();
                status = 400;
                bytes = "{\"code\":\"SEARCH_REQUIRED\",\"message\":\"public asset discovery requires a search query\"}".getBytes(StandardCharsets.UTF_8);
            } else {
                int start = parameters.containsKey("after") ? Integer.parseInt(parameters.get("after")) + 1 : 0;
                int end = Math.min(publicRows.size(), start + Integer.parseInt(parameters.get("limit")));
                JsonArray entries = new JsonArray();
                for (int index = start; index < end; index++) entries.add(asset(index, publicRows.get(index)));
                JsonObject page = new JsonObject();
                page.add("entries", entries);
                page.addProperty("has_more", end < publicRows.size());
                page.addProperty("next_cursor", end < publicRows.size() ? Integer.toString(end - 1) : null);
                status = 200;
                bytes = page.toString().getBytes(StandardCharsets.UTF_8);
            }
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try (HttpClient delegate = HttpClient.newHttpClient()) {
            CloudAssetClient client = client(delegate, server);
            List<String> ids = new ArrayList<>();
            String cursor = null;
            for (int pageIndex = 0; pageIndex < 10; pageIndex++) {
                CloudAssetPage page = client.listPage("public", "", cursor, 2).get(10, TimeUnit.SECONDS);
                assertTrue(page.entries().stream().allMatch(CloudAssetSummary::isPublic));
                assertTrue(page.entries().size() <= 2);
                assertEquals(pageIndex == 0, page.hasMore(), "private-only tail is not another public page");
                ids.addAll(page.entries().stream().map(entry -> entry.ref().assetId()).toList());
                if (!page.hasMore()) break;
                assertNotNull(page.nextCursor());
                assertNotEquals(cursor, page.nextCursor());
                cursor = page.nextCursor();
            }
            assertEquals(List.of("3", "5", "6", "8"), ids);
            assertEquals(1, rejected.get(), "remember compatibility for this authenticated client");
            assertTrue(requests.stream().anyMatch(path -> path.contains("scope=accessible")));
            assertTrue(requests.stream().noneMatch(path -> path.contains("q=")), "do not fake a wildcard search");
        } finally {
            server.stop(0);
        }
    }

    private static JsonObject asset(int index, boolean isPublic) {
        JsonObject result = new JsonObject();
        result.addProperty("asset_id", Integer.toString(index));
        result.addProperty("revision", 1);
        result.addProperty("raw_sha256", "a".repeat(64));
        result.addProperty("name", "模型 " + index);
        result.addProperty("format", "ysm");
        result.addProperty("byte_length", 12);
        result.addProperty("visibility", isPublic ? "PUBLIC" : "PRIVATE");
        return result;
    }

    private static CloudAssetClient client(HttpClient delegate, HttpServer server) {
        return new CloudAssetClient(new CloudHttpClient(
                CloudInstanceConfig.v1("fixture", URI.create("https://cloud.example.test")),
                new CloudPlayerPresenceClientTest.LoopbackClient(delegate,
                        URI.create("http://127.0.0.1:" + server.getAddress().getPort()))));
    }
}
