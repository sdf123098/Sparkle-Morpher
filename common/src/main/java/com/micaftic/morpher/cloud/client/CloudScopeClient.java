package com.micaftic.morpher.cloud.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micaftic.morpher.core.api.network.state.CloudErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** P2 scope/target/appearance operations against the selected Cloud instance. */
public final class CloudScopeClient {

    private final CloudHttpClient http;

    public CloudScopeClient(CloudHttpClient http) {
        this.http = Objects.requireNonNull(http, "http");
    }

    public CompletableFuture<List<CloudScope>> listScopes() {
        return http.getJson("/v1/scopes").thenApply(CloudScopeClient::parseScopes);
    }

    public CompletableFuture<List<CloudTarget>> listTargets(String scopeId) {
        return http.getJson("/v1/scopes/" + segment(scopeId) + "/targets").thenApply(CloudScopeClient::parseTargets);
    }

    public CompletableFuture<CloudEventRecovery> recoverEvents(String scopeId, long after, int limit) {
        if (after < 0) throw new IllegalArgumentException("after must not be negative");
        if (limit < 1 || limit > 256) throw new IllegalArgumentException("limit must be between 1 and 256");
        return http.getJson("/v1/scopes/" + segment(scopeId) + "/events/recovery?after=" + after + "&limit=" + limit)
                .thenApply(CloudScopeClient::parseRecovery);
    }

    public CompletableFuture<CloudAppearance> getAppearance(String targetId) {
        return http.getJson("/v1/targets/" + segment(targetId) + "/appearance").thenApply(CloudScopeClient::parseAppearance);
    }

    public CompletableFuture<CloudAppearance> updateAppearance(String targetId, CloudAppearanceUpdate update) {
        Objects.requireNonNull(update, "update");
        JsonObject body = new JsonObject();
        body.addProperty("request_id", update.requestId());
        body.addProperty("expected_revision", update.expectedRevision());
        if (update.assetId() == null) body.add("asset_id", com.google.gson.JsonNull.INSTANCE); else body.addProperty("asset_id", update.assetId());
        if (update.assetRevision() == null) body.add("asset_revision", com.google.gson.JsonNull.INSTANCE); else body.addProperty("asset_revision", update.assetRevision());
        if (update.rawSha256() == null) body.add("raw_sha256", com.google.gson.JsonNull.INSTANCE); else body.addProperty("raw_sha256", update.rawSha256());
        if (update.textureId() == null) body.add("texture_id", com.google.gson.JsonNull.INSTANCE); else body.addProperty("texture_id", update.textureId());
        if (update.scale() == null) body.add("scale", com.google.gson.JsonNull.INSTANCE); else body.addProperty("scale", update.scale());
        body.addProperty("disabled", update.disabled());
        return http.putJson("/v1/targets/" + segment(targetId) + "/appearance", body.toString()).thenApply(CloudScopeClient::parseAppearance);
    }

    static List<CloudScope> parseScopesForTest(String body) { return parseScopes(body); }

    static CloudEventRecovery parseRecoveryForTest(String body) { return parseRecovery(body); }

    static String segment(String value) {
        if (value == null || value.isBlank() || value.length() > 128 || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("Cloud path segment must be a slug");
        }
        return value;
    }

    private static List<CloudScope> parseScopes(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonArray()) throw new IllegalArgumentException("scope catalog must be an array");
            List<CloudScope> result = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray()) {
                JsonObject object = object(element);
                result.add(new CloudScope(string(object, "scope_id"), string(object, "tenant_id"), string(object, "name"), string(object, "world_epoch")));
            }
            return List.copyOf(result);
        } catch (RuntimeException e) {
            throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud scope catalog");
        }
    }

    private static List<CloudTarget> parseTargets(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonArray()) throw new IllegalArgumentException("target catalog must be an array");
            List<CloudTarget> result = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray()) {
                JsonObject object = object(element);
                result.add(new CloudTarget(string(object, "target_id"), string(object, "scope_id"), string(object, "kind"), string(object, "display_name"), object.get("revision").getAsLong()));
            }
            return List.copyOf(result);
        } catch (RuntimeException e) {
            throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud target catalog");
        }
    }

    private static CloudAppearance parseAppearance(String body) {
        try {
            return parseAppearance(JsonParser.parseString(body).getAsJsonObject());
        } catch (RuntimeException e) {
            throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud appearance");
        }
    }

    private static CloudAppearance parseAppearance(JsonObject object) {
        return new CloudAppearance(string(object, "target_id"), object.get("revision").getAsLong(), nullableString(object, "asset_id"), nullableLong(object, "asset_revision"), nullableString(object, "raw_sha256"), nullableString(object, "texture_id"), object.has("scale") && !object.get("scale").isJsonNull() ? object.get("scale").getAsFloat() : null, object.get("disabled").getAsBoolean());
    }

    private static CloudEventRecovery parseRecovery(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray entries = root.getAsJsonArray("entries");
            if (entries == null) throw new IllegalArgumentException("recovery entries are missing");
            List<CloudRecoveredEvent> result = new ArrayList<>();
            for (JsonElement element : entries) {
                JsonObject object = object(element);
                result.add(new CloudRecoveredEvent(object.get("sequence").getAsLong(), string(object, "event_id"), string(object, "kind"), parseAppearance(object.getAsJsonObject("payload"))));
            }
            return new CloudEventRecovery(root.get("from_cursor").getAsLong(), root.get("to_cursor").getAsLong(), root.get("has_more").getAsBoolean(), result);
        } catch (RuntimeException e) {
            throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud event recovery response");
        }
    }

    private static JsonObject object(JsonElement value) {
        if (!value.isJsonObject()) throw new IllegalArgumentException("Cloud catalog entry must be an object");
        return value.getAsJsonObject();
    }

    private static String string(JsonObject object, String name) {
        if (!object.has(name) || object.get(name).isJsonNull() || object.get(name).getAsString().isBlank()) throw new IllegalArgumentException("Missing Cloud field: " + name);
        return object.get(name).getAsString();
    }

    private static String nullableString(JsonObject object, String name) { return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : null; }
    private static Long nullableLong(JsonObject object, String name) { return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsLong() : null; }

    public record CloudScope(String scopeId, String tenantId, String name, String worldEpoch) {}
    public record CloudTarget(String targetId, String scopeId, String kind, String displayName, long revision) {}
    public record CloudAppearance(String targetId, long revision, String assetId, Long assetRevision, String rawSha256, String textureId, Float scale, boolean disabled) {}
    public record CloudEventRecovery(long fromCursor, long toCursor, boolean hasMore, List<CloudRecoveredEvent> events) {
        public CloudEventRecovery {
            if (fromCursor < 0 || toCursor < fromCursor) throw new IllegalArgumentException("invalid Cloud recovery cursor");
            events = events == null ? List.of() : List.copyOf(events);
        }
    }
    public record CloudRecoveredEvent(long sequence, String eventId, String kind, CloudAppearance appearance) {}
    public record CloudAppearanceUpdate(String requestId, long expectedRevision, String assetId, Long assetRevision, String rawSha256, String textureId, Float scale, boolean disabled) {
        public CloudAppearanceUpdate {
            if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();
            if (expectedRevision < 0) throw new IllegalArgumentException("expectedRevision must not be negative");
        }
    }
}
