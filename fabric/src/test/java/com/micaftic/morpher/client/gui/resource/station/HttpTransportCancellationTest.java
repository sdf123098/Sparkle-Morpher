package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.client.gui.resource.ModelRepoClient;
import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;
import com.micaftic.morpher.client.gui.resource.ResourceStationConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HttpTransportCancellationTest {
    @Test void cancellationInterruptsCandidateProbeAndDoesNotProbeTheNextMirror() throws Exception {
        CountDownLatch responseStarted = new CountDownLatch(1), releaseServer = new CountDownLatch(1);
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/model", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(200, 10);
            try (var stream = exchange.getResponseBody()) {
                stream.write(1);
                stream.flush();
                responseStarted.countDown();
                try { releaseServer.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
        server.start();
        DownloadCancellation cancellation = new DownloadCancellation();
        var config = new ResourceStationConfig.State(List.of(), "", 10000, 100, false, List.of());
        CompletableFuture<List<String>> ranking = CompletableFuture.supplyAsync(() -> rankCandidates(
                List.of(url(server) + "?first", url(server) + "?mirror"), config, cancellation));
        try {
            assertTrue(responseStarted.await(2, TimeUnit.SECONDS));
            cancellation.cancel();
            ExecutionException error = assertThrows(ExecutionException.class, () -> ranking.get(2, TimeUnit.SECONDS));
            assertInstanceOf(CancellationException.class, error.getCause());
            assertEquals(1, hits.get());
        } finally {
            releaseServer.countDown();
            server.stop(0);
        }
    }

    @Test void cancellingAStalledBodyUnblocksTheDownloadWorkerWithoutWaitingForReadTimeout() throws Exception {
        CountDownLatch bodyReceived = new CountDownLatch(1), releaseServer = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/model", exchange -> {
            exchange.sendResponseHeaders(200, 10);
            try (var stream = exchange.getResponseBody()) {
                stream.write(1);
                stream.flush();
                try { releaseServer.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
        server.start();
        DownloadCancellation cancellation = new DownloadCancellation();
        ModelRepoClient.ProgressListener listener = new ModelRepoClient.ProgressListener() {
            public void onProgress(int downloaded, int total) { if (downloaded > 0) bodyReceived.countDown(); }
            public boolean isCancelled() { return cancellation.isCancelled(); }
            public Runnable onCancel(Runnable action) { return cancellation.onCancel(action); }
        };
        CompletableFuture<byte[]> read = CompletableFuture.supplyAsync(() -> {
            try { return readBody(url(server), 10000, 100, listener); }
            catch (java.io.IOException e) { throw new java.util.concurrent.CompletionException(e); }
        });
        try {
            assertTrue(bodyReceived.await(2, TimeUnit.SECONDS));
            cancellation.cancel();
            ExecutionException error = assertThrows(ExecutionException.class, () -> read.get(2, TimeUnit.SECONDS));
            assertInstanceOf(CancellationException.class, error.getCause());
        } finally {
            releaseServer.countDown();
            server.stop(0);
        }
    }

    @Test void cancellationScopeInvokesOnceSupportsUnregisterAndLateRegistration() {
        DownloadCancellation cancellation = new DownloadCancellation();
        AtomicInteger called = new AtomicInteger();
        Runnable unregister = cancellation.onCancel(called::incrementAndGet);
        unregister.run();
        cancellation.onCancel(called::incrementAndGet);
        cancellation.cancel();
        cancellation.cancel();
        assertEquals(1, called.get());
        cancellation.onCancel(called::incrementAndGet);
        assertEquals(2, called.get());
        assertTrue(cancellation.isCancelled());
        assertThrows(CancellationException.class, cancellation::checkCancelled);
    }
    @Test void alreadyCancelledRequestNeverOpensAnHttpConnection() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = server(hits);
        try {
            var listener = new ModelRepoClient.ProgressListener() {
                public void onProgress(int downloaded, int total) {}
                public boolean isCancelled() { return true; }
            };
            assertThrows(CancellationException.class, () -> readBody(url(server), 1000, 100, listener));
            assertEquals(0, hits.get(), "Cancellation must be checked before getResponseCode opens the connection");
        } finally { server.stop(0); }
    }

    @Test void cancellationThrownByDownloadBudgetIsNotRetriedOrWrappedAsIoFailure() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = server(hits);
        try {
            var listener = new ModelRepoClient.ProgressListener() {
                public void onProgress(int downloaded, int total) { throw new CancellationException("budget exhausted"); }
            };
            String url = url(server);
            var entry = new ModelRepoEntry("model", url, "model.ysm", 4, "");
            var config = new ResourceStationConfig.State(List.of(), "", 1000, 100, false, List.of());
            assertThrows(CancellationException.class, () -> ModelRepoClient.download(entry, config, listener));
            assertEquals(1, hits.get());
        } finally { server.stop(0); }
    }

    private static HttpServer server(AtomicInteger hits) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/model", exchange -> {
            hits.incrementAndGet();
            byte[] data = new byte[]{1, 2, 3, 4};
            exchange.sendResponseHeaders(200, data.length);
            try (var stream = exchange.getResponseBody()) { stream.write(data); }
        });
        server.start();
        return server;
    }

    private static String url(HttpServer server) { return "http://127.0.0.1:" + server.getAddress().getPort() + "/model"; }

    private static byte[] readBody(String url, int timeoutMs, int maxBytes, ModelRepoClient.ProgressListener listener) throws java.io.IOException {
        var entry = new ModelRepoEntry("model", url, "model.zip", 0, "");
        var config = new ResourceStationConfig.State(List.of(), "", timeoutMs, maxBytes, false, List.of());
        return ModelRepoClient.download(entry, config, listener);
    }

    @SuppressWarnings("unchecked")
    private static List<String> rankCandidates(List<String> urls, ResourceStationConfig.State config, ModelRepoClient.ProgressListener listener) {
        try {
            var method = ModelRepoClient.class.getDeclaredMethod("rankCandidates", List.class, ResourceStationConfig.State.class, ModelRepoClient.ProgressListener.class);
            method.setAccessible(true);
            return (List<String>) method.invoke(null, urls, config, listener);
        } catch (java.lang.reflect.InvocationTargetException wrapped) {
            if (wrapped.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(wrapped.getCause());
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
}
