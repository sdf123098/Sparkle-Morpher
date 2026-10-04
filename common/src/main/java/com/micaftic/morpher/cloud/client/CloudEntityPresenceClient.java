package com.micaftic.morpher.cloud.client;

import com.google.gson.*;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Client-only, world-scoped discovery. Editing is authorized by the Cloud scope ACL. */
public final class CloudEntityPresenceClient {
    private final CloudHttpClient http;
    public record Entry(UUID entityId, CloudEntityProvider.Kind kind, String targetId,
                        long bindingRevision, long revision, CloudPlayerSelection selection) {}
    public CloudEntityPresenceClient(CloudHttpClient http) { this.http = Objects.requireNonNull(http); }
    private static String path(String worldKey) {
        if (worldKey == null || !worldKey.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid entity world key");
        return "/v1/entity-worlds/" + worldKey;
    }
    public CompletableFuture<Void> ensureWorld(String worldKey) {
        return http.postJson(path(worldKey), "{}").thenApply(ignored -> null);
    }
    public CompletableFuture<Long> revision(String worldKey, UUID entityId) {
        return http.getJson(path(worldKey) + "/entities/" + entityId + "/appearance")
                .thenApply(body -> JsonParser.parseString(body).getAsJsonObject().get("revision").getAsLong());
    }
    public CompletableFuture<Entry> publish(String worldKey, UUID entityId, CloudEntityProvider.Kind kind,
                                            String name, long expected, CloudPlayerSelection selection) {
        if (kind == CloudEntityProvider.Kind.PLAYER || expected < 0) throw new IllegalArgumentException("Invalid entity publication");
        JsonObject body = new JsonObject();
        body.addProperty("entity_kind", kind.wireValue());
        body.addProperty("display_name", name == null || name.isBlank() ? entityId.toString() : name);
        body.addProperty("expected_revision", expected);
        body.addProperty("share_model", true);
        if (selection == null) body.add("asset_id", JsonNull.INSTANCE);
        else {
            body.addProperty("asset_id", selection.ref().assetId());
            body.addProperty("asset_revision", selection.ref().revision());
            body.addProperty("raw_sha256", selection.ref().rawSha256());
            body.addProperty("texture_id", selection.textureId());
        }
        return http.putJson(path(worldKey) + "/entities/" + entityId + "/appearance", body.toString())
                .thenApply(result -> parseEntry(JsonParser.parseString(result).getAsJsonObject()));
    }
    public CompletableFuture<Map<UUID, Entry>> query(String worldKey, Collection<UUID> ids) {
        if (ids.size() > 64) throw new IllegalArgumentException("Cloud entity batches are limited to 64 UUIDs");
        JsonObject body = new JsonObject(); JsonArray uuids = new JsonArray();
        ids.forEach(id -> uuids.add(id.toString())); body.add("entity_uuids", uuids);
        return http.postJson(path(worldKey) + "/appearances/query", body.toString()).thenApply(this::parse);
    }
    public CompletableFuture<HttpResponse<byte[]>> download(String worldKey, Entry entry) {
        var ref = Objects.requireNonNull(entry.selection()).ref();
        return http.getBytes(path(worldKey) + "/entities/" + entry.entityId() + "/asset?asset_revision="
                + ref.revision() + "&raw_sha256=" + ref.rawSha256(), null, null);
    }
    Map<UUID, Entry> parse(String body) {
        Map<UUID, Entry> result = new HashMap<>();
        for (JsonElement element : JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("entries")) {
            Entry entry = parseEntry(element.getAsJsonObject());
            if (result.putIfAbsent(entry.entityId(), entry) != null) throw new IllegalArgumentException("Duplicate Cloud entity binding");
        }
        return Map.copyOf(result);
    }
    private Entry parseEntry(JsonObject row) {
        UUID id = UUID.fromString(row.get("entity_uuid").getAsString());
        var kind = CloudEntityProvider.Kind.fromWireValue(row.get("entity_kind").getAsString());
        if (kind == CloudEntityProvider.Kind.PLAYER) throw new IllegalArgumentException("Player presence is a separate protocol");
        long revision = row.get("revision").getAsLong(), binding = row.get("binding_revision").getAsLong();
        if (revision < 0 || binding < 0) throw new IllegalArgumentException("Invalid entity revision");
        CloudPlayerSelection selection = null;
        if (!row.get("selection").isJsonNull()) {
            JsonObject value = row.getAsJsonObject("selection");
            selection = new CloudPlayerSelection(http.instance().instanceId(),
                    CloudAssetCache.sha256(http.instance().origin().toString().getBytes(StandardCharsets.UTF_8)),
                    new CloudAssetRef(value.get("asset_id").getAsString(), value.get("asset_revision").getAsLong(), value.get("raw_sha256").getAsString()),
                    value.get("format").getAsString(), value.get("texture_id").getAsString());
        }
        return new Entry(id, kind, row.get("target_id").getAsString(), binding, revision, selection);
    }
}
