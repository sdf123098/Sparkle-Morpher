package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.client.gui.resource.ModelRepoClient;
import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;
import com.micaftic.morpher.client.gui.resource.ResourceStationConfig;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourcePreviewModelDownloadTest {
    @Test void previewGetsTheActualModelBytesWithoutImportingToDisk() throws Exception {
        withServer((entry, hits) -> {
            assertArrayEquals(new byte[]{1,2,3,4}, ModelRepoClient.downloadPreviewModel(entry, config(1024), new DownloadCancellation()));
            assertEquals(1, hits.get());
        });
    }

    @Test void declaredOversizeModelDoesNotStartAnHttpRequest() throws Exception {
        withServer((entry, hits) -> {
            var large = new ModelRepoEntry(entry.name(), entry.url(), entry.fileName(), EmbeddedModelPreview.MAX_MODEL_BYTES + 1L, "");
            assertThrows(IOException.class, () -> ModelRepoClient.downloadPreviewModel(large, config(Integer.MAX_VALUE), new DownloadCancellation()));
            assertEquals(0, hits.get());
        });
    }

    @Test void unknownSizeStillEnforcesTheTransferLimit() throws Exception {
        withServer((entry, hits) -> {
            assertThrows(IOException.class, () -> ModelRepoClient.downloadPreviewModel(entry, config(3), new DownloadCancellation()));
            assertEquals(1, hits.get());
        });
    }

    @Test void pageBudgetCancellationStopsTheModelRequest() throws Exception {
        withServer((entry, hits) -> {
            var cancellation = new DownloadCancellation();
            assertThrows(CancellationException.class, () -> ModelRepoClient.downloadPreviewModel(entry, config(1024), new ResourcePreviewBudget(3).listener(cancellation)));
            assertEquals(1, hits.get());
            assertTrue(cancellation.isCancelled());
        });
    }

    private static ResourceStationConfig.State config(int maxBytes) {
        return new ResourceStationConfig.State(List.of(), "", 1000, maxBytes, false, List.of());
    }
    @FunctionalInterface private interface Check { void run(ModelRepoEntry entry, AtomicInteger hits) throws Exception; }
    private static void withServer(Check check) throws Exception {
        var hits = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/model", exchange -> {
            hits.incrementAndGet();
            byte[] bytes = {1,2,3,4};
            exchange.sendResponseHeaders(200, bytes.length);
            try(var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        try { check.run(new ModelRepoEntry("preview", "http://127.0.0.1:"+server.getAddress().getPort()+"/model", "preview.zip", 0, ""), hits); }
        finally { server.stop(0); }
    }
}
