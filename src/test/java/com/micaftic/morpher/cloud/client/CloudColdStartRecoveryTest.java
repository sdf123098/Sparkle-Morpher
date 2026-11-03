package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import com.micaftic.morpher.core.storage.ModelStoragePaths;
import com.micaftic.morpher.util.LocalModelSelectionStore;
import com.micaftic.morpher.core.model.CloudAssetIdentity;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CloudColdStartRecoveryTest {
    @TempDir Path root;
    @Test void emptyCatalogRecoversPersistedFormatAndTextureWithoutAnAssetFormatHeader() throws Exception {
        byte[] bytes="in-memory-only-model".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var asset=new CloudAssetSummary(new CloudAssetRef("skin",2,CloudAssetCache.sha256(bytes)),"skin","glb",bytes.length);
        String model=new CloudAssetIdentity("official","catalog","skin","2",asset.ref().rawSha256()).runtimeModelId();
        Path choice=root.resolve("local_model_selection.json");
        LocalModelSelectionStore.save(choice,model,"blue");
        new CloudModelSelectionStore.Index(root.resolve("cloud_model_selection.json")).recordApplied("official",asset);
        var requests=new AtomicInteger();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/assets/skin/revisions/2/content",exchange->{
            int attempt=requests.incrementAndGet();
            assertEquals("Bearer session",exchange.getRequestHeaders().getFirst("Authorization"));
            if(attempt==1 || attempt==3) {exchange.sendResponseHeaders(attempt==1?503:403,-1);exchange.close();return;}
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        ModelStoragePaths.withRoot(root);
        try(var delegate=HttpClient.newHttpClient()) {
            var client=new CloudAssetClient(new CloudHttpClient(CloudInstanceConfig.v1("official",URI.create("https://cloud.example.test")),
                new CloudPlayerPresenceClientTest.LoopbackClient(delegate,URI.create("http://127.0.0.1:"+server.getAddress().getPort())),"session"));
            var catalog=new CloudAssetCatalogStore();
            assertTrue(catalog.snapshot().isEmpty());
            assertThrows(ExecutionException.class,()->CloudClientRuntime.RuntimeState.downloadCatalogAsset(client,catalog,()->1024,asset.ref()).get(10,TimeUnit.SECONDS));
            assertEquals(model,LocalModelSelectionStore.load(choice).getLeft());
            var recovered=CloudClientRuntime.RuntimeState.downloadCatalogAsset(client,catalog,()->1024,asset.ref()).get(10,TimeUnit.SECONDS);
            assertEquals("glb",recovered.format());assertArrayEquals(bytes,recovered.bytes());assertEquals(2,requests.get());
            assertThrows(ExecutionException.class,()->CloudClientRuntime.RuntimeState.downloadCatalogAsset(client,catalog,()->1024,asset.ref()).get(10,TimeUnit.SECONDS));
            var saved=LocalModelSelectionStore.load(choice);assertEquals(model,saved.getLeft());assertEquals("blue",saved.getRight());
            assertEquals(asset,CloudSelectedModelRecovery.findAsset("official",model,new CloudModelSelectionStore.Index(root.resolve("cloud_model_selection.json")).read("official",false)));
            assertNull(CloudSelectedModelRecovery.savedFormat(new CloudAssetRef("skin",3,asset.ref().rawSha256()),List.of(asset)));
            assertNull(CloudSelectedModelRecovery.savedFormat(new CloudAssetRef("skin",2,"f".repeat(64)),List.of(asset)));
            assertEquals(Set.of("local_model_selection.json","cloud_model_selection.json"),new HashSet<>(Files.list(root).map(p->p.getFileName().toString()).toList()));
        } finally {ModelStoragePaths.withRoot(null);server.stop(0);}
    }

    @Test void previouslyAppliedPublicAssetUsesItsSavedVisibilityForCdnRecovery() throws Exception {
        byte[] bytes = "public-cold-start".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var asset = new CloudAssetSummary(new CloudAssetRef("folder/笙黑 +100%", 4, CloudAssetCache.sha256(bytes)),
                "public skin", "ysm", bytes.length, "PUBLIC");
        new CloudModelSelectionStore.Index(root.resolve("cloud_model_selection.json")).recordApplied("community", asset);
        var publicRequests = new AtomicInteger();
        var authenticatedRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String rawPath = exchange.getRequestURI().getRawPath();
            if (rawPath.startsWith("/v1/public/assets/")) {
                publicRequests.incrementAndGet();
                assertEquals("/v1/public/assets/folder%2F%E7%AC%99%E9%BB%91%20%2B100%25/revisions/4/content", rawPath);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } else {
                authenticatedRequests.incrementAndGet();
                exchange.sendResponseHeaders(500, -1);
            }
            exchange.close();
        });
        server.start();
        ModelStoragePaths.withRoot(root);
        try (var delegate = HttpClient.newHttpClient()) {
            var client = new CloudAssetClient(new CloudHttpClient(
                    CloudInstanceConfig.v1("community", URI.create("https://cloud.example.test")),
                    new CloudPlayerPresenceClientTest.LoopbackClient(delegate,
                            URI.create("http://127.0.0.1:" + server.getAddress().getPort())), "session"));
            var recovered = CloudClientRuntime.RuntimeState.downloadCatalogAsset(
                    client, new CloudAssetCatalogStore(), () -> 1024, asset.ref()).get(10, TimeUnit.SECONDS);
            assertArrayEquals(bytes, recovered.bytes());
            assertEquals("ysm", recovered.format());
            assertEquals(1, publicRequests.get());
            assertEquals(0, authenticatedRequests.get());
        } finally {
            ModelStoragePaths.withRoot(null);
            server.stop(0);
        }
    }
}
