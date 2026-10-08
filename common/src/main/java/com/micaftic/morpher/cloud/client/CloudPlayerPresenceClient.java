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
        return publish(identityId, entityId, expected, selection, nameProof, null);
    }

    public CompletableFuture<Long> publish(String identityId, UUID entityId, long expected, CloudPlayerSelection selection, JsonObject nameProof, CloudPlayerMotion motion) {
        return publish(identityId, entityId, expected, selection, nameProof, motion, null);
    }

    public CompletableFuture<Long> publish(String identityId, UUID entityId, long expected, CloudPlayerSelection selection, JsonObject nameProof, CloudPlayerMotion motion, JsonObject displayState) {
        JsonObject body = new JsonObject();
        body.addProperty("identity_id", CloudScopeClient.segment(identityId));
        body.addProperty("entity_uuid", entityId.toString()); body.addProperty("expected_revision", expected);
        if (nameProof != null) body.add("profile_name_proof", nameProof.deepCopy());
        body.add("motion", selection == null || motion == null ? JsonNull.INSTANCE : motion.toJson());
        if (selection != null && displayState != null) body.add("display_state", displayState.deepCopy());
        if (selection == null) body.add("asset_id", JsonNull.INSTANCE);
        else {
            body.addProperty("asset_id", selection.ref().assetId()); body.addProperty("asset_revision", selection.ref().revision());
            body.addProperty("raw_sha256", selection.ref().rawSha256()); body.addProperty("texture_id", selection.textureId());
        }
        return http.visualJson("PUT", "/v1/players/me/appearance", body.toString(), 64 * 1024)
                .thenApply(result -> JsonParser.parseString(result).getAsJsonObject().get("revision").getAsLong());
    }

    public CompletableFuture<Map<UUID, CloudPlayerSelection>> query(Collection<UUID> ids) {
        if (ids.size() > 64) throw new IllegalArgumentException("Cloud player batches are limited to 64 UUIDs");
        JsonObject body = new JsonObject(); JsonArray uuids = new JsonArray();
        ids.forEach(id -> uuids.add(id.toString())); body.add("entity_uuids", uuids);
        Set<UUID> requested = Set.copyOf(ids);
        return http.visualJson("POST", "/v1/players/appearances/query", body.toString(), 16 * 1024 * 1024).thenApply(response -> {
            Map<UUID, CloudPlayerSelection> result = parse(response);
            if (!requested.containsAll(result.keySet())) throw new IllegalArgumentException("Unrequested Cloud player UUID");
            return result;
        });
    }

    Map<UUID, CloudPlayerSelection> parse(String body) {
        Map<UUID, CloudPlayerSelection> result = new HashMap<>();
        String instance = http.instance().instanceId();
        String origin = CloudAssetCache.sha256(http.instance().origin().toString().getBytes(StandardCharsets.UTF_8));
        JsonArray entries = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("entries");
        if (entries.size() > 64) throw new IllegalArgumentException("Cloud player response exceeds batch limit");
        for (JsonElement element : entries) {
            JsonObject row = element.getAsJsonObject(); UUID id = UUID.fromString(row.get("entity_uuid").getAsString());
            CloudPlayerSelection selection = null;
            if (!row.get("selection").isJsonNull()) {
                JsonObject value = row.getAsJsonObject("selection");
                selection = new CloudPlayerSelection(instance, origin, new CloudAssetRef(value.get("asset_id").getAsString(),
                        value.get("asset_revision").getAsLong(), value.get("raw_sha256").getAsString()),
                        value.get("format").getAsString(), value.get("texture_id").getAsString(),
                        CloudPlayerMotion.fromJson(value.get("motion")), row.has("revision") ? row.get("revision").getAsLong() : 0,
                        CloudPlayerDisplayState.fromJson(value.get("display_state")));
            }
            if (result.containsKey(id)) throw new IllegalArgumentException("Duplicate Cloud player UUID");
            result.put(id, selection);
        }
        return Collections.unmodifiableMap(result);
    }
}
