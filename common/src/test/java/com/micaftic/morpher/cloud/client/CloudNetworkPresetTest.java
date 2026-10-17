package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import com.micaftic.morpher.core.network.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.net.ssl.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CloudNetworkPresetTest {
    private static final CloudInstanceConfig INSTANCE=CloudInstanceConfig.v1("test",URI.create("https://cloud.example.test"));
    @TempDir Path temporary;
    @AfterEach void reset() { CloudClientRuntime.clear(); NetworkAccess.update(NetworkPreset.AUTO,false); }
    @Test void restrictedModesBlockEveryHttpRequestShapeIncludingLoginAndRefresh() {
        try(var transport=new CountingClient()) {
            var client=new CloudHttpClient(INSTANCE,transport,"retained-token");
            for(var preset:List.of(NetworkPreset.PEER_ONLY,NetworkPreset.LOCAL_ONLY)) {
                NetworkAccess.update(preset,false);
                for(var future:List.of(client.discoverInstance(),client.getJson("/v1/assets"),client.postJson("/v1/sessions","{}"),
                        client.visualJson("PUT","/v1/state","{}",1024),client.getBytes("/v1/bytes",null,null),client.authorizeAsset("/v1/bytes","a".repeat(64)),
                        client.uploadAsset(new byte[]{1},"asset","asset","ysm","a".repeat(64),"request"),
                        new CloudAuthClient(client).login("account","password"),new CloudAuthClient(client).refresh("retained-refresh")))
                    assertThrows(CompletionException.class,future::join);
            }
            assertEquals(0,transport.calls.get());
            NetworkAccess.update(NetworkPreset.AUTO,false);client.getJson("/v1/assets");
            assertEquals(1,transport.calls.get());assertEquals("Bearer retained-token",transport.last.headers().firstValue("Authorization").orElseThrow());
        }
    }
    @Test void webSocketConnectorIsNotCalledWhenDisabledAndAccountStateSurvives() {
        NetworkAccess.update(NetworkPreset.PEER_ONLY,false);var calls=new AtomicInteger();
        try(var realtime=new CloudRealtimeClient(INSTANCE,"token","test",ignored->{},()->{calls.incrementAndGet();return CompletableFuture.failedFuture(new AssertionError("Unexpected connection"));})) {
            assertThrows(CompletionException.class,()->realtime.connect().join());assertEquals(0,calls.get());
        }
        var session=new CloudSession("access","refresh",3600,86400);
        CloudClientRuntime.configure(INSTANCE,session,temporary,"test");
        var runtime=CloudClientRuntime.state();assertNotNull(runtime);assertSame(session,runtime.session());
        NetworkAccess.update(NetworkPreset.LOCAL_ONLY,false);assertSame(runtime,CloudClientRuntime.state());
        NetworkAccess.update(NetworkPreset.AUTO,false);assertSame(session,CloudClientRuntime.state().session());
    }
    @Test void lateSocketAndOldListenerCannotResumeAfterAPolicyRoundTrip() throws Exception {
        NetworkAccess.update(NetworkPreset.AUTO,false);
        var aborted = new AtomicInteger();
        WebSocket socket = (WebSocket) java.lang.reflect.Proxy.newProxyInstance(WebSocket.class.getClassLoader(),new Class<?>[]{WebSocket.class},(proxy,method,args)-> {
            if(method.getName().equals("abort")) { aborted.incrementAndGet(); return null; }
            if(method.getReturnType()==CompletableFuture.class) return CompletableFuture.completedFuture(proxy);
            if(method.getReturnType()==boolean.class) return false;
            if(method.getReturnType()==String.class) return "";
            return null;
        });
        var upstream = new CompletableFuture<WebSocket>() {
            @Override public boolean cancel(boolean interrupt) { return false; }
        };
        try(var realtime = new CloudRealtimeClient(INSTANCE,"token","test",ignored->fail("Stale event applied"),()->upstream)) {
            var connecting = realtime.connect();
            Class<?> listenerType = Arrays.stream(CloudRealtimeClient.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("Listener")).findFirst().orElseThrow();
            var constructor = listenerType.getDeclaredConstructor(CloudRealtimeClient.class,long.class,long.class);constructor.setAccessible(true);
            var oldListener = (WebSocket.Listener) constructor.newInstance(realtime,1L,NetworkAccess.state().epoch());
            NetworkAccess.update(NetworkPreset.LOCAL_ONLY,false);realtime.suspend();
            NetworkAccess.update(NetworkPreset.AUTO,false);
            upstream.complete(socket);
            assertThrows(CompletionException.class,connecting::join);
            assertEquals(1,aborted.get());
            oldListener.onOpen(socket);
            oldListener.onBinary(socket,java.nio.ByteBuffer.wrap(new byte[]{1}),true);
            assertEquals(3,aborted.get());
        }
    }
    private static final class CountingClient extends HttpClient {
        final HttpClient delegate=HttpClient.newHttpClient(); final AtomicInteger calls=new AtomicInteger(); HttpRequest last;
        public Optional<CookieHandler> cookieHandler(){return delegate.cookieHandler();} public Optional<Duration> connectTimeout(){return delegate.connectTimeout();}
        public Redirect followRedirects(){return delegate.followRedirects();} public Optional<ProxySelector> proxy(){return delegate.proxy();}
        public SSLContext sslContext(){return delegate.sslContext();} public SSLParameters sslParameters(){return delegate.sslParameters();}
        public Optional<Authenticator> authenticator(){return delegate.authenticator();} public Version version(){return delegate.version();}
        public Optional<Executor> executor(){return delegate.executor();}
        public <T>HttpResponse<T> send(HttpRequest request,HttpResponse.BodyHandler<T> handler){throw new AssertionError("Synchronous HTTP is not allowed");}
        public <T>CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,HttpResponse.BodyHandler<T> handler){calls.incrementAndGet();last=request;return CompletableFuture.failedFuture(new IllegalStateException("Captured request"));}
        public <T>CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,HttpResponse.BodyHandler<T> handler,HttpResponse.PushPromiseHandler<T> push){return sendAsync(request,handler);}
        @Override public void close(){delegate.close();}
    }
}
