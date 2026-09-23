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

    public CompletableFuture<CloudScope> createScope(CloudScopeCreate create) {
        Objects.requireNonNull(create, "create");
        JsonObject body = new JsonObject();
        body.addProperty("scope_id", segment(create.scopeId()));
        body.addProperty("name", requiredText(create.name(), "name"));
        body.addProperty("world_epoch", segment(create.worldEpoch()));
        if (create.offlinePolicy() == null) body.add("offline_policy", com.google.gson.JsonNull.INSTANCE);
        else body.addProperty("offline_policy", requiredText(create.offlinePolicy(), "offlinePolicy"));
        return http.postJson("/v1/scopes", body.toString()).thenApply(CloudScopeClient::parseScope);
    }

    public CompletableFuture<List<CloudTarget>> listTargets(String scopeId) {
        return http.getJson("/v1/scopes/" + segment(scopeId) + "/targets").thenApply(CloudScopeClient::parseTargets);
    }

    public CompletableFuture<CloudTarget> createTarget(CloudTargetCreate create) {
        Objects.requireNonNull(create, "create");
        JsonObject body = new JsonObject();
        body.addProperty("scope_id", segment(create.scopeId()));
        if (create.targetId() == null) body.add("target_id", com.google.gson.JsonNull.INSTANCE);
        else body.addProperty("target_id", segment(create.targetId()));
        body.addProperty("kind", requiredText(create.kind(), "kind"));
        body.addProperty("display_name", requiredText(create.displayName(), "displayName"));
        return http.postJson("/v1/targets", body.toString()).thenApply(CloudScopeClient::parseTarget);
    }

    public CompletableFuture<List<CloudAclEntry>> listScopeAcl(String scopeId) {
        return http.getJson("/v1/scopes/" + segment(scopeId) + "/acl").thenApply(CloudScopeClient::parseAcl);
    }

    public CompletableFuture<CloudAclEntry> setScopeAcl(String scopeId, CloudAclUpdate update) {
        Objects.requireNonNull(update, "update");
        return http.putJson("/v1/scopes/" + segment(scopeId) + "/acl", aclBody(update).toString())
                .thenApply(CloudScopeClient::parseAclEntry);
    }

    public CompletableFuture<List<CloudEntityBinding>> listBindings(String scopeId) {
        return http.getJson("/v1/scopes/" + segment(scopeId) + "/bindings").thenApply(CloudScopeClient::parseBindings);
    }

    public CompletableFuture<CloudEntityBinding> registerBinding(String scopeId, CloudBindingRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        JsonObject body = new JsonObject();
        body.addProperty("world_epoch", registration.worldEpoch());
        body.addProperty("entity_uuid", registration.entityUuid());
        body.addProperty("entity_kind", registration.entityKind());
        body.addProperty("target_id", registration.targetId());
        return http.postJson("/v1/scopes/" + segment(scopeId) + "/bindings", body.toString()).thenApply(CloudScopeClient::parseBinding);
    }

    public CompletableFuture<CloudEntityBinding> observeBinding(String bindingId, CloudBindingObservation observation) {
        Objects.requireNonNull(observation, "observation");
        JsonObject body = new JsonObject();
        body.addProperty("world_epoch", observation.worldEpoch());
        body.addProperty("observation_state", observation.observationState());
        return http.putJson("/v1/bindings/" + segment(bindingId) + "/observation", body.toString()).thenApply(CloudScopeClient::parseBinding);
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

    public CompletableFuture<List<CloudAclEntry>> listTargetAcl(String targetId) {
        return http.getJson("/v1/targets/" + segment(targetId) + "/acl").thenApply(CloudScopeClient::parseAcl);
    }

    public CompletableFuture<CloudAclEntry> setTargetAcl(String targetId, CloudAclUpdate update) {
        Objects.requireNonNull(update, "update");
        return http.putJson("/v1/targets/" + segment(targetId) + "/acl", aclBody(update).toString())
                .thenApply(CloudScopeClient::parseAclEntry);
    }

    static List<CloudScope> parseScopesForTest(String body) { return parseScopes(body); }

    static CloudScope parseScopeForTest(String body) { return parseScope(body); }

    static CloudTarget parseTargetForTest(String body) { return parseTarget(body); }

    static List<CloudAclEntry> parseAclForTest(String body) { return parseAcl(body); }

    static CloudEventRecovery parseRecoveryForTest(String body) { return parseRecovery(body); }

    static List<CloudEntityBinding> parseBindingsForTest(String body) { return parseBindings(body); }

    static String segment(String value) {
        if (value == null || value.isBlank() || value.length() > 128 || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("Cloud path segment must be a slug");
        }
        return value;
    }

    private static String requiredText(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 256 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(name + " must be a non-empty single-line value");
        }
        return value;
    }

    private static List<CloudScope> parseScopes(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonArray()) throw new IllegalArgumentException("scope catalog must be an array");
            List<CloudScope> result = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray()) {
                result.add(parseScope(object(element)));
            }
            return List.copyOf(result);
        } catch (RuntimeException e) {
            throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud scope catalog");
        }
    }

    private static CloudScope parseScope(String body) {
        try { return parseScope(JsonParser.parseString(body).getAsJsonObject()); }
        catch (RuntimeException e) { throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud scope"); }
    }

    private static CloudScope parseScope(JsonObject object) {
        return new CloudScope(string(object, "scope_id"), string(object, "tenant_id"), string(object, "name"), string(object, "world_epoch"), nullableString(object, "offline_policy"));
    }

    private static List<CloudTarget> parseTargets(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonArray()) throw new IllegalArgumentException("target catalog must be an array");
            List<CloudTarget> result = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray()) {
                result.add(parseTarget(object(element)));
            }
            return List.copyOf(result);
        } catch (RuntimeException e) {
            throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud target catalog");
        }
    }

    private static CloudTarget parseTarget(String body) {
        try { return parseTarget(JsonParser.parseString(body).getAsJsonObject()); }
        catch (RuntimeException e) { throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud target"); }
    }

    private static CloudTarget parseTarget(JsonObject object) {
        return new CloudTarget(string(object, "target_id"), string(object, "scope_id"), string(object, "kind"), string(object, "display_name"), object.get("revision").getAsLong());
    }

    private static List<CloudAclEntry> parseAcl(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonArray()) throw new IllegalArgumentException("ACL must be an array");
            List<CloudAclEntry> result = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray()) result.add(parseAclEntry(object(element)));
            return List.copyOf(result);
        } catch (RuntimeException e) {
            throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud ACL");
        }
    }

    private static CloudAclEntry parseAclEntry(String body) {
        try { return parseAclEntry(JsonParser.parseString(body).getAsJsonObject()); }
        catch (RuntimeException e) { throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud ACL entry"); }
    }

    private static CloudAclEntry parseAclEntry(JsonObject object) {
        return new CloudAclEntry(string(object, "account_id"), string(object, "role"));
    }

    private static JsonObject aclBody(CloudAclUpdate update) {
        JsonObject body = new JsonObject();
        body.addProperty("account_id", requiredText(update.accountId(), "accountId"));
        body.addProperty("role", requiredText(update.role(), "role"));
        return body;
    }

    private static List<CloudEntityBinding> parseBindings(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonArray()) throw new IllegalArgumentException("binding catalog must be an array");
            List<CloudEntityBinding> result = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray()) result.add(parseBinding(object(element)));
            return List.copyOf(result);
        } catch (RuntimeException e) {
            throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud binding catalog");
        }
    }

    private static CloudEntityBinding parseBinding(String body) {
        try { return parseBinding(JsonParser.parseString(body).getAsJsonObject()); }
        catch (RuntimeException e) { throw new CloudHttpException(200, CloudErrorCode.MALFORMED_MESSAGE, "Malformed Cloud entity binding"); }
    }

    private static CloudEntityBinding parseBinding(JsonObject object) {
        return new CloudEntityBinding(string(object, "binding_id"), string(object, "scope_id"), string(object, "world_epoch"), string(object, "entity_uuid"), string(object, "entity_kind"), string(object, "target_id"), string(object, "observation_state"), nullableString(object, "last_seen_at"), object.get("revision").getAsLong());
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

    public record CloudScope(String scopeId, String tenantId, String name, String worldEpoch, String offlinePolicy) {
        public CloudScope(String scopeId, String tenantId, String name, String worldEpoch) {
            this(scopeId, tenantId, name, worldEpoch, "STRICT_APPROVAL");
        }
    }
    public record CloudTarget(String targetId, String scopeId, String kind, String displayName, long revision) {}
    public record CloudEntityBinding(String bindingId, String scopeId, String worldEpoch, String entityUuid, String entityKind, String targetId, String observationState, String lastSeenAt, long revision) {}
    public record CloudBindingRegistration(String worldEpoch, String entityUuid, String entityKind, String targetId) {}
    public record CloudBindingObservation(String worldEpoch, String observationState) {}
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
    public record CloudScopeCreate(String scopeId, String name, String worldEpoch, String offlinePolicy) {}
    public record CloudTargetCreate(String scopeId, String targetId, String kind, String displayName) {}
    public record CloudAclEntry(String accountId, String role) {}
    public record CloudAclUpdate(String accountId, String role) {}
}
