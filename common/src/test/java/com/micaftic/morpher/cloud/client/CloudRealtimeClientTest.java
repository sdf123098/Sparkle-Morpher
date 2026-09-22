package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudRealtimeClientTest {

    @Test
    void protobufEnvelopeRoundTripsUnknownPayload() {
        CloudRealtimeClient.CloudRealtimeEnvelope original = new CloudRealtimeClient.CloudRealtimeEnvelope(
                CloudInstanceConfig.PROTOCOL_V1, "TargetSnapshot", "request", "event", "scope", new byte[]{1, 2, 3});

        CloudRealtimeClient.CloudRealtimeMessage decoded = CloudRealtimeClient.decodeEnvelopeForTest(
                CloudRealtimeClient.encodeEnvelopeForTest(original));

        assertEquals(original.protocolVersion(), decoded.protocolVersion());
        assertEquals(original.kind(), decoded.kind());
        assertEquals(original.requestId(), decoded.requestId());
        assertEquals(original.eventId(), decoded.eventId());
        assertEquals(original.scopeId(), decoded.scopeId());
        assertArrayEquals(original.payload(), decoded.payload());
    }

    @Test
    void envelopeRejectsTruncatedInput() {
        assertThrows(IllegalArgumentException.class, () ->
                CloudRealtimeClient.decodeEnvelopeForTest(new byte[]{0x0A, 0x05, 's'}));
    }

    @Test
    void realtimeClientDerivesTrustedWebsocketEndpoint() {
        CloudInstanceConfig config = CloudInstanceConfig.v1("local", URI.create("https://cloud.example.org"));
        assertEquals("wss://cloud.example.org/v1/realtime", config.websocketUri().toString());
    }
}
