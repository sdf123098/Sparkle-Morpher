package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;
import javax.net.ssl.*;
import static org.junit.jupiter.api.Assertions.*;

class CloudDisplayCancellationTest {
    @Test
    void closingMaterializationCancelsVerificationAdmissionAndActualHttpFuture() {
        try (var transport = new PendingClient()) {
            var http = new CloudHttpClient(CloudInstanceConfig.v1("test", URI.create("https://test.invalid")), transport, "account-one");
            var assets = new CloudAssetClient(http);
            var ref = new CloudAssetRef("asset", 1, "a".repeat(64));
            var catalog = new CloudAssetCatalogStore();
            catalog.upsert(new CloudAssetSummary(ref, "asset", "ysm", 10));
            var coordinator = new CloudAssetMaterializationCoordinator(catalog,
                    exact -> assets.downloadForDisplay(exact, "ysm", 128));
            var consumer = coordinator.ensure(ref);
            assertFalse(transport.pending.isDone());
            coordinator.close();
            assertTrue(consumer.isCancelled());
            assertTrue(transport.pending.isCancelled(), "cancel must reach the HTTP exchange, not stop at a dependent future");
        }
    }

    @Test
    void lifecycleCancellationCoversBothBinaryDownloadAndResidentAclRequests() {
        try (var transport = new PendingClient()) {
            var http = new CloudHttpClient(CloudInstanceConfig.v1("test", URI.create("https://test.invalid")), transport, "account-one");
            var assets = new CloudAssetClient(http);
            var ref = new CloudAssetRef("asset", 1, "a".repeat(64));
            var download = assets.downloadForDisplay(ref, "ysm", 128);
            var downloadExchange = transport.pending;
            var authorization = assets.authorizeDisplay(ref);
            var aclExchange = transport.pending;
            http.cancelDisplayRequests();
            assertTrue(downloadExchange.isCancelled()); assertTrue(aclExchange.isCancelled());
            assertTrue(download.isCompletedExceptionally()); assertTrue(authorization.isCompletedExceptionally());
        }
    }

    @Test
    void bytesAlreadyDownloadedBeforeWorldChangeAreRejectedByDelayedConsumers() {
        try (var transport = new PendingClient()) {
            var http = new CloudHttpClient(CloudInstanceConfig.v1("test", URI.create("https://test.invalid")), transport, "account-one");
            byte[] bytes = new byte[]{1, 2, 3};
            var download = http.getBoundedBytes("/v1/assets/asset/revisions/1/content", 128);
            transport.respond(bytes);
            assertSame(bytes, download.join().body());
            assertTrue(CloudHttpClient.currentDisplayBytes(bytes));
            CloudClientRuntime.clear();
            assertFalse(CloudHttpClient.currentDisplayBytes(bytes), "a queued old callback must not acquire the new world's generation");
        }
    }

    @Test
    void obsoleteDownloadResponseCannotBeDeliveredAfterWorldChange() {
        try (var transport = new PendingClient()) {
            var http = new CloudHttpClient(CloudInstanceConfig.v1("test", URI.create("https://test.invalid")), transport, "account-one");
            var download = http.getBoundedBytes("/v1/assets/asset/revisions/1/content", 128);
            CloudClientRuntime.clear();
            transport.respond(new byte[]{1});
            assertTrue(download.isCompletedExceptionally());
        }
    }

    private static final class PendingClient extends HttpClient {
        private final HttpClient delegate = HttpClient.newHttpClient();
        private CompletableFuture<?> pending;
        public Optional<CookieHandler> cookieHandler() { return delegate.cookieHandler(); }
        public Optional<Duration> connectTimeout() { return delegate.connectTimeout(); }
        public Redirect followRedirects() { return delegate.followRedirects(); }
        public Optional<ProxySelector> proxy() { return delegate.proxy(); }
        public SSLContext sslContext() { return delegate.sslContext(); }
        public SSLParameters sslParameters() { return delegate.sslParameters(); }
        public Optional<Authenticator> authenticator() { return delegate.authenticator(); }
        public Version version() { return delegate.version(); }
        public Optional<Executor> executor() { return delegate.executor(); }
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) { throw new AssertionError(); }
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            var result = new CompletableFuture<HttpResponse<T>>(); pending = result; return result;
        }
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> push) { return sendAsync(request, handler); }
        @SuppressWarnings("unchecked")
        void respond(byte[] bytes) {
            ((CompletableFuture<HttpResponse<byte[]>>) pending).complete(new HttpResponse<>() {
                public int statusCode() { return 200; }
                public HttpRequest request() { return HttpRequest.newBuilder(URI.create("https://test.invalid")).build(); }
                public Optional<HttpResponse<byte[]>> previousResponse() { return Optional.empty(); }
                public HttpHeaders headers() { return HttpHeaders.of(java.util.Map.of(), (name, value) -> true); }
                public byte[] body() { return bytes; }
                public Optional<SSLSession> sslSession() { return Optional.empty(); }
                public URI uri() { return URI.create("https://test.invalid"); }
                public Version version() { return Version.HTTP_1_1; }
            });
        }
        @Override public void close() { delegate.close(); }
    }
}
