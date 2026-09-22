package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import com.micaftic.morpher.core.api.network.state.CloudConnectionStatus;
import com.micaftic.morpher.core.api.network.state.CloudErrorCode;
import com.micaftic.morpher.core.api.network.state.CloudState;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Small Java-only v1 realtime boundary. Minecraft server networking is not
 * involved: the selected Cloud origin owns the WSS session and snapshots.
 */
public final class CloudRealtimeClient implements AutoCloseable {

    private static final int MAX_MESSAGE_BYTES = 64 * 1024;

    private final CloudInstanceConfig instance;
    private final String accessToken;
    private final String clientVersion;
    private final Consumer<CloudRealtimeMessage> messageConsumer;
    private final HttpClient httpClient;
    private volatile WebSocket socket;
    private volatile boolean closed;

    public CloudRealtimeClient(
            CloudInstanceConfig instance,
            String accessToken,
            String clientVersion,
            Consumer<CloudRealtimeMessage> messageConsumer
    ) {
        this(instance, accessToken, clientVersion, messageConsumer,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());
    }

    CloudRealtimeClient(
            CloudInstanceConfig instance,
            String accessToken,
            String clientVersion,
            Consumer<CloudRealtimeMessage> messageConsumer,
            HttpClient httpClient
    ) {
        this.instance = Objects.requireNonNull(instance, "instance");
        this.accessToken = requireToken(accessToken);
        this.clientVersion = requireText(clientVersion, "clientVersion");
        this.messageConsumer = Objects.requireNonNull(messageConsumer, "messageConsumer");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    public CloudInstanceConfig instance() {
        return instance;
    }

    public CompletableFuture<CloudRealtimeClient> connect() {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Cloud realtime client is closed"));
        CloudState.setStatus(CloudConnectionStatus.CONNECTING, CloudErrorCode.NONE, instance.instanceId());
        WebSocket.Builder builder = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken);
        return builder.buildAsync(instance.websocketUri(), new Listener())
                .thenApply(webSocket -> {
                    socket = webSocket;
                    return this;
                })
                .whenComplete((ignored, failure) -> {
                    if (failure != null && !closed) {
                        CloudState.setStatus(CloudConnectionStatus.DISCONNECTED, CloudErrorCode.INTERNAL, instance.instanceId());
                    }
                });
    }

    public CompletableFuture<Void> joinScope(String scopeId, String worldEpoch) {
        return send(new CloudRealtimeEnvelope(CloudInstanceConfig.PROTOCOL_V1, "JoinScope", UUID.randomUUID().toString(), "", scopeId, Proto.joinScope(scopeId, worldEpoch)));
    }

    public CompletableFuture<Void> heartbeat(long clientTimeUnixMs) {
        return send(new CloudRealtimeEnvelope(CloudInstanceConfig.PROTOCOL_V1, "Heartbeat", UUID.randomUUID().toString(), "", "", Proto.heartbeat(clientTimeUnixMs)));
    }

    public CompletableFuture<Void> send(CloudRealtimeEnvelope envelope) {
        WebSocket current = socket;
        if (closed || current == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Cloud realtime client is not connected"));
        }
        byte[] bytes = Proto.envelope(envelope.protocolVersion(), envelope.kind(), envelope.requestId(), envelope.eventId(), envelope.scopeId(), envelope.payload());
        if (bytes.length > MAX_MESSAGE_BYTES) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Cloud realtime message exceeds 64 KiB"));
        }
        return current.sendBinary(ByteBuffer.wrap(bytes), true).thenApply(ignored -> null);
    }

    static byte[] encodeEnvelopeForTest(CloudRealtimeEnvelope envelope) {
        return Proto.envelope(envelope.protocolVersion(), envelope.kind(), envelope.requestId(), envelope.eventId(), envelope.scopeId(), envelope.payload());
    }

    static CloudRealtimeMessage decodeEnvelopeForTest(byte[] bytes) {
        return Proto.decodeEnvelope(bytes);
    }

    @Override
    public void close() {
        closed = true;
        WebSocket current = socket;
        socket = null;
        if (current != null) {
            current.sendClose(WebSocket.NORMAL_CLOSURE, "closed");
        }
        CloudState.setStatus(CloudConnectionStatus.DISCONNECTED, CloudErrorCode.NONE, null);
    }

    public record CloudRealtimeEnvelope(
            String protocolVersion,
            String kind,
            String requestId,
            String eventId,
            String scopeId,
            byte[] payload
    ) {
        public CloudRealtimeEnvelope {
            protocolVersion = requireText(protocolVersion, "protocolVersion");
            kind = requireText(kind, "kind");
            requestId = requestId == null ? "" : requestId;
            eventId = eventId == null ? "" : eventId;
            scopeId = scopeId == null ? "" : scopeId;
            payload = payload == null ? new byte[0] : payload.clone();
        }

        public byte[] payload() {
            return payload.clone();
        }
    }

    public record CloudRealtimeMessage(
            String protocolVersion,
            String kind,
            String requestId,
            String eventId,
            String scopeId,
            byte[] payload
    ) {
        public CloudRealtimeMessage {
            payload = payload == null ? new byte[0] : payload.clone();
        }

        public byte[] payload() {
            return payload.clone();
        }
    }

    private final class Listener implements WebSocket.Listener {
        private final ByteArrayOutputStream fragments = new ByteArrayOutputStream();

        @Override
        public void onOpen(WebSocket webSocket) {
            socket = webSocket;
            webSocket.request(1);
            byte[] hello = Proto.hello(clientVersion, instance.instanceId());
            byte[] envelope = Proto.envelope(CloudInstanceConfig.PROTOCOL_V1, "Hello", UUID.randomUUID().toString(), "", "", hello);
            webSocket.sendBinary(ByteBuffer.wrap(envelope), true);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            if (fragments.size() + data.remaining() > MAX_MESSAGE_BYTES) {
                CloudState.setStatus(CloudConnectionStatus.DEGRADED, CloudErrorCode.MESSAGE_TOO_LARGE, instance.instanceId());
                webSocket.sendClose(1002, "message too large");
                return CompletableFuture.completedFuture(null);
            }
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            fragments.writeBytes(bytes);
            if (last) {
                try {
                    CloudRealtimeMessage message = Proto.decodeEnvelope(fragments.toByteArray());
                    if (!CloudInstanceConfig.PROTOCOL_V1.equals(message.protocolVersion())) {
                        CloudState.setStatus(CloudConnectionStatus.DEGRADED, CloudErrorCode.PROTOCOL_UNSUPPORTED, instance.instanceId());
                    } else if ("HelloAck".equals(message.kind())) {
                        CloudState.setStatus(CloudConnectionStatus.READY, CloudErrorCode.NONE, instance.instanceId());
                    } else if ("Error".equals(message.kind())) {
                        CloudState.setStatus(CloudConnectionStatus.DEGRADED, CloudErrorCode.MALFORMED_MESSAGE, instance.instanceId());
                    }
                    messageConsumer.accept(message);
                } catch (RuntimeException failure) {
                    CloudState.setStatus(CloudConnectionStatus.DEGRADED, CloudErrorCode.MALFORMED_MESSAGE, instance.instanceId());
                } finally {
                    fragments.reset();
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (!closed) CloudState.setStatus(CloudConnectionStatus.DISCONNECTED, CloudErrorCode.NONE, instance.instanceId());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (!closed) CloudState.setStatus(CloudConnectionStatus.DISCONNECTED, CloudErrorCode.INTERNAL, instance.instanceId());
        }
    }

    private static String requireToken(String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("accessToken must not be blank");
        return token;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    private static final class Proto {
        private Proto() {}

        static byte[] hello(String clientVersion, String instanceId) {
            return message(fieldString(1, clientVersion), fieldString(2, CloudInstanceConfig.PROTOCOL_V1), fieldString(3, instanceId));
        }

        static byte[] joinScope(String scopeId, String worldEpoch) {
            return message(fieldString(1, requireText(scopeId, "scopeId")), fieldString(2, requireText(worldEpoch, "worldEpoch")));
        }

        static byte[] heartbeat(long time) {
            return message(fieldVarint(1, time));
        }

        static byte[] envelope(String protocol, String kind, String requestId, String eventId, String scopeId, byte[] payload) {
            return message(fieldString(1, protocol), fieldString(2, kind), fieldString(3, requestId), fieldString(4, eventId), fieldString(5, scopeId), fieldBytes(6, payload));
        }

        static CloudRealtimeMessage decodeEnvelope(byte[] bytes) {
            String protocol = "";
            String kind = "";
            String requestId = "";
            String eventId = "";
            String scopeId = "";
            byte[] payload = new byte[0];
            Reader reader = new Reader(bytes);
            while (reader.hasRemaining()) {
                long tag = reader.varint();
                int field = (int) (tag >>> 3);
                int wire = (int) (tag & 7);
                if (wire != 2) {
                    reader.skip(wire);
                    continue;
                }
                byte[] value = reader.bytes();
                switch (field) {
                    case 1 -> protocol = new String(value, StandardCharsets.UTF_8);
                    case 2 -> kind = new String(value, StandardCharsets.UTF_8);
                    case 3 -> requestId = new String(value, StandardCharsets.UTF_8);
                    case 4 -> eventId = new String(value, StandardCharsets.UTF_8);
                    case 5 -> scopeId = new String(value, StandardCharsets.UTF_8);
                    case 6 -> payload = value;
                    default -> { }
                }
            }
            if (protocol.isBlank() || kind.isBlank()) throw new IllegalArgumentException("Cloud envelope misses protocol or kind");
            return new CloudRealtimeMessage(protocol, kind, requestId, eventId, scopeId, payload);
        }

        private static byte[] message(byte[]... fields) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (byte[] field : fields) output.writeBytes(field);
            return output.toByteArray();
        }

        private static byte[] fieldString(int field, String value) { return fieldBytes(field, value.getBytes(StandardCharsets.UTF_8)); }

        private static byte[] fieldBytes(int field, byte[] value) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            writeVarint(output, ((long) field << 3) | 2);
            writeVarint(output, value.length);
            output.writeBytes(value);
            return output.toByteArray();
        }

        private static byte[] fieldVarint(int field, long value) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            writeVarint(output, (long) field << 3);
            writeVarint(output, value);
            return output.toByteArray();
        }

        private static void writeVarint(ByteArrayOutputStream output, long value) {
            while ((value & ~0x7fL) != 0) {
                output.write((int) (value & 0x7f) | 0x80);
                value >>>= 7;
            }
            output.write((int) value);
        }

        private static final class Reader {
            private final byte[] bytes;
            private int offset;

            Reader(byte[] bytes) { this.bytes = bytes; }
            boolean hasRemaining() { return offset < bytes.length; }

            long varint() {
                long result = 0;
                for (int shift = 0; shift < 64; shift += 7) {
                    if (!hasRemaining()) throw new IllegalArgumentException("truncated protobuf varint");
                    int value = bytes[offset++] & 0xff;
                    result |= (long) (value & 0x7f) << shift;
                    if ((value & 0x80) == 0) return result;
                }
                throw new IllegalArgumentException("protobuf varint overflow");
            }

            byte[] bytes() {
                long length = varint();
                if (length < 0 || length > bytes.length - offset) throw new IllegalArgumentException("invalid protobuf length");
                byte[] value = java.util.Arrays.copyOfRange(bytes, offset, offset + (int) length);
                offset += (int) length;
                return value;
            }

            void skip(int wire) {
                switch (wire) {
                    case 0 -> varint();
                    case 1 -> advance(8);
                    case 2 -> bytes();
                    case 5 -> advance(4);
                    default -> throw new IllegalArgumentException("unsupported protobuf wire type: " + wire);
                }
            }

            void advance(int length) {
                if (length < 0 || length > bytes.length - offset) throw new IllegalArgumentException("truncated protobuf field");
                offset += length;
            }
        }
    }
}
