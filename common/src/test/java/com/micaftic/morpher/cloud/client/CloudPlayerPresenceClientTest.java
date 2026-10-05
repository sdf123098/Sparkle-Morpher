package com.micaftic.morpher.cloud.client;
import com.micaftic.morpher.cloud.CloudInstanceConfig;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

import java.net.URI;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CloudPlayerPresenceClientTest {
    private final CloudPlayerPresenceClient api = new CloudPlayerPresenceClient(new CloudHttpClient(
            CloudInstanceConfig.v1("official", URI.create("https://cloud.example.test"))));
    private final String a = "aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa", b = "bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb";
    private String selection(String name, String texture) {
        return "{\"asset_id\":\""+name+"\",\"asset_revision\":2,\"raw_sha256\":\""+"a".repeat(64)+"\",\"format\":\"ysm\",\"texture_id\":\""+texture+"\"}";
    }
    @Test void mapsModelsAndTexturesToExactObservedPlayers() {
        var entries=api.parse("{\"entries\":[{\"entity_uuid\":\""+a+"\",\"selection\":"+selection("昔涟","一")+"},{\"entity_uuid\":\""+b+"\",\"selection\":"+selection("符玄","二")+"}]}");
        assertEquals("昔涟",entries.get(UUID.fromString(a)).ref().assetId());
        assertEquals("符玄",entries.get(UUID.fromString(b)).ref().assetId());
        assertEquals("二",entries.get(UUID.fromString(b)).textureId());
        assertNotEquals(entries.get(UUID.fromString(a)).runtimeModelId(),entries.get(UUID.fromString(b)).runtimeModelId());
    }
    @Test void preservesExplicitPrivateMissingAndExpiredClears() {
        var entries=api.parse("{\"entries\":[{\"entity_uuid\":\""+a+"\",\"selection\":null}]}");
        assertTrue(entries.containsKey(UUID.fromString(a))); assertNull(entries.get(UUID.fromString(a)));
    }
    @Test void rejectsDuplicatePlayerIncludingNullTombstone() {
        assertThrows(IllegalArgumentException.class,()->api.parse("{\"entries\":[{\"entity_uuid\":\""+a+"\",\"selection\":null},{\"entity_uuid\":\""+a+"\",\"selection\":"+selection("x","default")+"}]}"));
    }
    @Test void rejectsUntrustedAssetHashAndOversizedQuery() {
        assertThrows(IllegalArgumentException.class,()->api.parse("{\"entries\":[{\"entity_uuid\":\""+a+"\",\"selection\":"+selection("x","default").replace("a".repeat(64),"bad")+"}]}"));
        assertThrows(IllegalArgumentException.class,()->api.query(java.util.Collections.nCopies(65,UUID.fromString(a))));
    }

    @Test void sendsAuthenticatedObservedUuidCasAndUtf8TextureOverHttp() throws Exception {
        var state = new CloudPlayerMotionState();
        state.play("坐", 1000); state.roaming(Map.of("bq", 4f));
        state.controller("player.post_main", "长时间待机", 1200, Map.of("idle_random2", 1f));
        var motion = state.snapshot();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var requests=new java.util.concurrent.CopyOnWriteArrayList<String[]>();
        server.createContext("/v1/players",exchange -> {
            String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            requests.add(new String[]{exchange.getRequestMethod(),exchange.getRequestURI().toString(),exchange.getRequestHeaders().getFirst("Authorization"),body});
            var responseSelection = JsonParser.parseString(selection("fuxuan","贴图二")).getAsJsonObject();
            responseSelection.add("motion", motion.toJson());
            String result=exchange.getRequestURI().getPath().endsWith("/query")
                ? "{\"entries\":[{\"entity_uuid\":\""+b+"\",\"revision\":7,\"selection\":"+responseSelection+"}]}"
                : "{\"revision\":7}";
            byte[] bytes=result.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try (HttpClient delegate=HttpClient.newHttpClient()) {
            var instance=CloudInstanceConfig.v1("official",URI.create("https://cloud.example.test"));
            var client=new CloudPlayerPresenceClient(new CloudHttpClient(instance,
                new LoopbackClient(delegate,URI.create("http://127.0.0.1:"+server.getAddress().getPort())),"isolated-test-session"));
            assertEquals(7L,client.revision("identity_current").get(10,TimeUnit.SECONDS));
            var ref=new CloudAssetRef("fuxuan",2,"a".repeat(64));
            var selected=new CloudPlayerSelection("official",CloudAssetCache.sha256(instance.origin().toString().getBytes(StandardCharsets.UTF_8)),ref,"ysm","贴图二");
            assertEquals(7L,client.publish("identity_current",UUID.fromString(b),6,selected,null,motion).get(10,TimeUnit.SECONDS));
            var observed = client.query(List.of(UUID.fromString(b))).get(10,TimeUnit.SECONDS).get(UUID.fromString(b));
            assertEquals("贴图二",observed.textureId());
            assertEquals(motion, observed.motion());
            assertEquals(7L, observed.appearanceRevision());
            assertEquals(selected, observed.withoutMotion());
            client.publish("identity_current",UUID.fromString(b),7,null,JsonParser.parseString("{\"value\":\"public-signed-property\",\"signature\":\"public-signature\"}").getAsJsonObject()).get(10,TimeUnit.SECONDS);
            assertEquals("GET",requests.get(0)[0]);
            assertEquals("/v1/players/me/appearance?identity_id=identity_current",requests.get(0)[1]);
            for(var request:requests) assertEquals("Bearer isolated-test-session",request[2]);
            assertEquals("PUT",requests.get(1)[0]);
            var publication=JsonParser.parseString(requests.get(1)[3]).getAsJsonObject();
            assertEquals(b,publication.get("entity_uuid").getAsString());
            assertEquals(6,publication.get("expected_revision").getAsInt());
            assertEquals("贴图二",publication.get("texture_id").getAsString());
            assertTrue(publication.has("motion"), "Cloud player publications must include an explicit motion envelope, including clears");
            assertEquals(motion, CloudPlayerMotion.fromJson(publication.get("motion")));
            assertEquals("POST",requests.get(2)[0]);
            assertEquals(b,JsonParser.parseString(requests.get(2)[3]).getAsJsonObject().getAsJsonArray("entity_uuids").get(0).getAsString());
            assertTrue(JsonParser.parseString(requests.get(3)[3]).getAsJsonObject().get("asset_id").isJsonNull());
            assertTrue(JsonParser.parseString(requests.get(3)[3]).getAsJsonObject().get("motion").isJsonNull());
            assertEquals("public-signed-property",JsonParser.parseString(requests.get(3)[3]).getAsJsonObject().getAsJsonObject("profile_name_proof").get("value").getAsString());
        } finally {server.stop(0);}
    }
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
            HttpRequest.Builder builder = HttpRequest.newBuilder(origin.resolve(request.uri().getRawPath() + (request.uri().getRawQuery() == null ? "" : "?" + request.uri().getRawQuery())));
            request.headers().map().forEach((key, values) -> values.forEach(value -> builder.header(key, value)));
            builder.method(request.method(), request.bodyPublisher().orElseGet(HttpRequest.BodyPublishers::noBody));
            return delegate.sendAsync(builder.build(), handler);
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushHandler) { return sendAsync(request, handler); }
    }

}
