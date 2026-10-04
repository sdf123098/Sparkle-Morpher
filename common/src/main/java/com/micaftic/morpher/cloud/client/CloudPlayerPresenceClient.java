package com.micaftic.morpher.cloud.client;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.nio.charset.StandardCharsets;

/** Authenticated Cloud-only player discovery. No Minecraft payload or server handshake. */
public final class CloudPlayerPresenceClient {
    private final CloudHttpClient http;
    public CloudPlayerPresenceClient(CloudHttpClient http) { this.http = Objects.requireNonNull(http); }

    public CompletableFuture<Long> revision(String identityId) {
        return http.getJson("/v1/players/me/appearance?identity_id=" + CloudScopeClient.segment(identityId))
                .thenApply(body -> JsonParser.parseString(body).getAsJsonObject().get("revision").getAsLong());
    }

    public CompletableFuture<Long> publish(String identityId, UUID entityId, long expected, CloudPlayerSelection selection) {
        return publish(identityId, entityId, expected, selection, null);
    }

    public CompletableFuture<Long> publish(String identityId, UUID entityId, long expected, CloudPlayerSelection selection, JsonObject nameProof) {
        JsonObject body = new JsonObject();
        body.addProperty("identity_id", CloudScopeClient.segment(identityId));
        body.addProperty("entity_uuid", entityId.toString()); body.addProperty("expected_revision", expected);
        if (nameProof != null) body.add("profile_name_proof", nameProof.deepCopy());
        if (selection == null) body.add("asset_id", JsonNull.INSTANCE);
        else {
            body.addProperty("asset_id", selection.ref().assetId()); body.addProperty("asset_revision", selection.ref().revision());
            body.addProperty("raw_sha256", selection.ref().rawSha256()); body.addProperty("texture_id", selection.textureId());
        }
        return http.putJson("/v1/players/me/appearance", body.toString())
                .thenApply(result -> JsonParser.parseString(result).getAsJsonObject().get("revision").getAsLong());
    }

    public CompletableFuture<Map<UUID, CloudPlayerSelection>> query(Collection<UUID> ids) {
        if (ids.size() > 64) throw new IllegalArgumentException("Cloud player batches are limited to 64 UUIDs");
        JsonObject body = new JsonObject(); JsonArray uuids = new JsonArray();
        ids.forEach(id -> uuids.add(id.toString())); body.add("entity_uuids", uuids);
        return http.postJson("/v1/players/appearances/query", body.toString()).thenApply(this::parse);
    }

    Map<UUID, CloudPlayerSelection> parse(String body) {
        Map<UUID, CloudPlayerSelection> result = new HashMap<>();
        String instance = http.instance().instanceId();
        String origin = CloudAssetCache.sha256(http.instance().origin().toString().getBytes(StandardCharsets.UTF_8));
        for (JsonElement element : JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("entries")) {
            JsonObject row = element.getAsJsonObject(); UUID id = UUID.fromString(row.get("entity_uuid").getAsString());
            CloudPlayerSelection selection = null;
            if (!row.get("selection").isJsonNull()) {
                JsonObject value = row.getAsJsonObject("selection");
                selection = new CloudPlayerSelection(instance, origin, new CloudAssetRef(value.get("asset_id").getAsString(),
                        value.get("asset_revision").getAsLong(), value.get("raw_sha256").getAsString()),
                        value.get("format").getAsString(), value.get("texture_id").getAsString());
            }
            if (result.containsKey(id)) throw new IllegalArgumentException("Duplicate Cloud player UUID");
            result.put(id, selection);
        }
        return Collections.unmodifiableMap(result);
    }
}
