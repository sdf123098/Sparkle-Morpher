package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void decodesTargetSnapshotPayload() {
        CloudRealtimeClient.CloudRealtimeMessage message = new CloudRealtimeClient.CloudRealtimeMessage(
                CloudInstanceConfig.PROTOCOL_V1,
                "TargetSnapshot",
                "request",
                "",
                "scope",
                CloudRealtimeClient.targetSnapshotPayloadForTest("snapshot", "target", "PLAYER", "Player", 4));

        CloudRealtimeClient.CloudRealtimeEvent event = CloudRealtimeClient.decodeEventForTest(message);

        assertEquals("snapshot", event.snapshotId());
        assertEquals(1, event.targets().size());
        assertEquals("target", event.targets().get(0).targetId());
        assertEquals(4, event.targets().get(0).revision());
    }

    @Test
    void decodesAppearancePayloadAndRetainsEventIdentity() {
        CloudRealtimeClient.CloudRealtimeMessage message = new CloudRealtimeClient.CloudRealtimeMessage(
                CloudInstanceConfig.PROTOCOL_V1,
                "AppearanceState",
                "",
                "event-1",
                "scope",
                CloudRealtimeClient.appearanceStatePayloadForTest("target", 7, "texture", 1.25F, true));

        CloudRealtimeClient.CloudRealtimeEvent event = CloudRealtimeClient.decodeEventForTest(message);

        assertEquals("event-1", event.eventId());
        assertNotNull(event.appearance());
        assertEquals(7, event.appearance().revision());
        assertEquals("texture", event.appearance().textureId());
        assertEquals(1.25F, event.appearance().scale());
        assertTrue(event.appearance().disabled());
    }
}
