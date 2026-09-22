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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Consumer<CloudRealtimeEvent> eventConsumer;
    private final Map<String, Long> targetRevisions = new ConcurrentHashMap<>();
    private final Set<String> seenEventIds = ConcurrentHashMap.newKeySet();
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
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(), ignored -> { });
    }

    public CloudRealtimeClient(
            CloudInstanceConfig instance,
            String accessToken,
            String clientVersion,
            Consumer<CloudRealtimeMessage> messageConsumer,
            Consumer<CloudRealtimeEvent> eventConsumer
    ) {
        this(instance, accessToken, clientVersion, messageConsumer,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(), eventConsumer);
    }

    CloudRealtimeClient(
            CloudInstanceConfig instance,
            String accessToken,
            String clientVersion,
            Consumer<CloudRealtimeMessage> messageConsumer,
            HttpClient httpClient
    ) {
        this(instance, accessToken, clientVersion, messageConsumer, httpClient, ignored -> { });
    }

    CloudRealtimeClient(
            CloudInstanceConfig instance,
            String accessToken,
            String clientVersion,
            Consumer<CloudRealtimeMessage> messageConsumer,
            HttpClient httpClient,
            Consumer<CloudRealtimeEvent> eventConsumer
    ) {
        this.instance = Objects.requireNonNull(instance, "instance");
        this.accessToken = requireToken(accessToken);
        this.clientVersion = requireText(clientVersion, "clientVersion");
        this.messageConsumer = Objects.requireNonNull(messageConsumer, "messageConsumer");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.eventConsumer = Objects.requireNonNull(eventConsumer, "eventConsumer");
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

    static CloudRealtimeEvent decodeEventForTest(CloudRealtimeMessage message) {
        return Proto.decodeEvent(message);
    }

    static byte[] targetSnapshotPayloadForTest(String snapshotId, String targetId, String kind, String displayName, long revision) {
        return Proto.targetSnapshot(snapshotId, targetId, kind, displayName, revision);
    }

    static byte[] appearanceStatePayloadForTest(String targetId, long revision, String textureId, float scale, boolean disabled) {
        return Proto.appearanceState(targetId, revision, textureId, scale, disabled);
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

    public record CloudRealtimeEvent(
            String kind,
            String eventId,
            String scopeId,
            String snapshotId,
            List<CloudScopeClient.CloudTarget> targets,
            CloudScopeClient.CloudAppearance appearance
    ) {
        public CloudRealtimeEvent {
            kind = requireText(kind, "kind");
            eventId = eventId == null ? "" : eventId;
            scopeId = scopeId == null ? "" : scopeId;
            snapshotId = snapshotId == null ? "" : snapshotId;
            targets = targets == null ? List.of() : List.copyOf(targets);
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
                    CloudRealtimeEvent event = Proto.decodeEvent(message);
                    if (event != null && acceptEvent(event)) eventConsumer.accept(event);
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

    private boolean acceptEvent(CloudRealtimeEvent event) {
        if ("AppearanceState".equals(event.kind()) && event.appearance() != null) {
            if (!event.eventId().isBlank() && !seenEventIds.add(event.eventId())) return false;
            long revision = event.appearance().revision();
            Long previous = targetRevisions.putIfAbsent(event.appearance().targetId(), revision);
            if (previous != null && revision <= previous) return false;
            if (previous != null) targetRevisions.put(event.appearance().targetId(), revision);
        } else if ("TargetSnapshot".equals(event.kind())) {
            for (CloudScopeClient.CloudTarget target : event.targets()) {
                targetRevisions.merge(target.targetId(), target.revision(), Math::max);
            }
        }
        return true;
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

        static byte[] targetSnapshot(String snapshotId, String targetId, String kind, String displayName, long revision) {
            byte[] target = message(fieldString(1, targetId), fieldString(2, kind), fieldString(3, displayName), fieldVarint(4, revision));
            return message(fieldString(1, snapshotId), fieldBytes(2, target));
        }

        static byte[] appearanceState(String targetId, long revision, String textureId, float scale, boolean disabled) {
            return message(fieldString(1, targetId), fieldVarint(2, revision), fieldString(6, textureId), fieldFixed32(7, scale), fieldVarint(8, disabled ? 1 : 0));
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

        static CloudRealtimeEvent decodeEvent(CloudRealtimeMessage message) {
            return switch (message.kind()) {
                case "TargetSnapshot" -> decodeTargetSnapshot(message);
                case "AppearanceState" -> decodeAppearanceState(message);
                default -> null;
            };
        }

        private static CloudRealtimeEvent decodeTargetSnapshot(CloudRealtimeMessage message) {
            String snapshotId = "";
            List<CloudScopeClient.CloudTarget> targets = new ArrayList<>();
            Reader reader = new Reader(message.payload());
            while (reader.hasRemaining()) {
                long tag = reader.varint();
                int field = (int) (tag >>> 3);
                int wire = (int) (tag & 7);
                if (field == 1 && wire == 2) snapshotId = reader.string();
                else if (field == 2 && wire == 2) targets.add(decodeTargetEntry(reader.bytes(), message.scopeId()));
                else reader.skip(wire);
            }
            return new CloudRealtimeEvent(message.kind(), message.eventId(), message.scopeId(), snapshotId, targets, null);
        }

        private static CloudScopeClient.CloudTarget decodeTargetEntry(byte[] bytes, String scopeId) {
            String targetId = "";
            String kind = "";
            String displayName = "";
            long revision = 0;
            Reader reader = new Reader(bytes);
            while (reader.hasRemaining()) {
                long tag = reader.varint();
                int field = (int) (tag >>> 3);
                int wire = (int) (tag & 7);
                if (field == 1 && wire == 2) targetId = reader.string();
                else if (field == 2 && wire == 2) kind = reader.string();
                else if (field == 3 && wire == 2) displayName = reader.string();
                else if (field == 4 && wire == 0) revision = reader.varint();
                else reader.skip(wire);
            }
            if (targetId.isBlank() || kind.isBlank() || displayName.isBlank()) throw new IllegalArgumentException("TargetSnapshot entry is incomplete");
            return new CloudScopeClient.CloudTarget(targetId, scopeId, kind, displayName, revision);
        }

        private static CloudRealtimeEvent decodeAppearanceState(CloudRealtimeMessage message) {
            String targetId = "";
            String assetId = "";
            String rawSha256 = "";
            String textureId = "";
            long revision = 0;
            long assetRevision = 0;
            float scale = 0;
            boolean disabled = false;
            Reader reader = new Reader(message.payload());
            while (reader.hasRemaining()) {
                long tag = reader.varint();
                int field = (int) (tag >>> 3);
                int wire = (int) (tag & 7);
                if (field == 1 && wire == 2) targetId = reader.string();
                else if (field == 2 && wire == 0) revision = reader.varint();
                else if (field == 3 && wire == 2) assetId = reader.string();
                else if (field == 4 && wire == 0) assetRevision = reader.varint();
                else if (field == 5 && wire == 2) rawSha256 = reader.string();
                else if (field == 6 && wire == 2) textureId = reader.string();
                else if (field == 7 && wire == 5) scale = Float.intBitsToFloat(reader.fixed32());
                else if (field == 8 && wire == 0) disabled = reader.varint() != 0;
                else reader.skip(wire);
            }
            if (targetId.isBlank()) throw new IllegalArgumentException("AppearanceState misses target_id");
            CloudScopeClient.CloudAppearance appearance = new CloudScopeClient.CloudAppearance(targetId, revision,
                    assetId.isBlank() ? null : assetId, assetRevision == 0 ? null : assetRevision,
                    rawSha256.isBlank() ? null : rawSha256, textureId.isBlank() ? null : textureId, scale, disabled);
            return new CloudRealtimeEvent(message.kind(), message.eventId(), message.scopeId(), "", List.of(), appearance);
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

        private static byte[] fieldFixed32(int field, float value) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            writeVarint(output, ((long) field << 3) | 5);
            int bits = Float.floatToIntBits(value);
            output.write(bits & 0xff);
            output.write((bits >>> 8) & 0xff);
            output.write((bits >>> 16) & 0xff);
            output.write((bits >>> 24) & 0xff);
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

            String string() { return new String(bytes(), StandardCharsets.UTF_8); }

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

            int fixed32() {
                if (4 > bytes.length - offset) throw new IllegalArgumentException("truncated protobuf fixed32");
                int value = (bytes[offset] & 0xff)
                        | ((bytes[offset + 1] & 0xff) << 8)
                        | ((bytes[offset + 2] & 0xff) << 16)
                        | ((bytes[offset + 3] & 0xff) << 24);
                offset += 4;
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
