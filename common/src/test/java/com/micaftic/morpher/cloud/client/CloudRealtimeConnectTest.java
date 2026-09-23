package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudRealtimeConnectTest {
    @Test
    void concurrentConnectCallsShareOneWebSocketHandshake() {
        CompletableFuture<WebSocket> handshake = new CompletableFuture<>();
        int[] attempts = {0};
        CloudInstanceConfig instance = CloudInstanceConfig.v1("test", URI.create("https://localhost"));
        CloudRealtimeClient client = new CloudRealtimeClient(instance, "token", "2.0.0", ignored -> { }, () -> {
            attempts[0]++;
            return handshake;
        });

        CompletableFuture<CloudRealtimeClient> first = client.connect();
        CompletableFuture<CloudRealtimeClient> second = client.connect();

        assertEquals(first, second);
        assertTrue(!handshake.isDone());
        assertTrue(attempts[0] == 1);
        client.close();
    }

    @Test
    void repeatedConnectAfterOpenDoesNotReplaceTheActiveSocket() {
        CompletableFuture<WebSocket> handshake = CompletableFuture.completedFuture(new StubWebSocket());
        int[] attempts = {0};
        CloudInstanceConfig instance = CloudInstanceConfig.v1("test", URI.create("https://localhost"));
        CloudRealtimeClient client = new CloudRealtimeClient(instance, "token", "2.0.0", ignored -> { }, () -> {
            attempts[0]++;
            return handshake;
        });

        CompletableFuture<CloudRealtimeClient> first = client.connect();
        CompletableFuture<CloudRealtimeClient> second = client.connect();

        assertEquals(first.join(), second.join());
        assertTrue(attempts[0] == 1);
        client.close();
    }

    private static final class StubWebSocket implements WebSocket {
        @Override public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) { return CompletableFuture.completedFuture(this); }
        @Override public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) { return CompletableFuture.completedFuture(this); }
        @Override public CompletableFuture<WebSocket> sendPing(ByteBuffer message) { return CompletableFuture.completedFuture(this); }
        @Override public CompletableFuture<WebSocket> sendPong(ByteBuffer message) { return CompletableFuture.completedFuture(this); }
        @Override public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) { return CompletableFuture.completedFuture(this); }
        @Override public void request(long n) { }
        @Override public String getSubprotocol() { return ""; }
        @Override public boolean isOutputClosed() { return false; }
        @Override public boolean isInputClosed() { return false; }
        @Override public void abort() { }
    }
}
