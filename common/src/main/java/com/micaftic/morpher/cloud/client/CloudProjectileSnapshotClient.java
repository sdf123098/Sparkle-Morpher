package com.micaftic.morpher.cloud.client;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Small metadata requests; exact revision content uses the existing authenticated asset API. */
public final class CloudProjectileSnapshotClient {
    public record Lease(ProjectileAppearanceSnapshot snapshot, long revision, long serverTimeMs,
            long expiresAtMs, long absoluteExpiresAtMs) {
        public Lease {
            if (revision < 1 || revision >= 9007199254740991L || serverTimeMs < 0
                    || serverTimeMs >= 9007199254740991L || expiresAtMs < 0 || expiresAtMs >= 9007199254740991L
                    || absoluteExpiresAtMs < expiresAtMs || absoluteExpiresAtMs >= 9007199254740991L
                    || expiresAtMs-serverTimeMs>86400000L || absoluteExpiresAtMs-serverTimeMs>604800000L) throw new IllegalArgumentException("Invalid projectile lease");
        }
        public long deadlineNanos(long receivedNanos) {
            long remainingMs = Math.max(0, Math.min(expiresAtMs, absoluteExpiresAtMs) - serverTimeMs);
            // A peer clock cannot extend permission; bounded to the advertised protocol maximum.
            return receivedNanos + Math.min(remainingMs, 86400000L) * 1000000L;
        }
    }
    private final CloudHttpClient http;
    private final CloudInstanceInfo info;
    public CloudProjectileSnapshotClient(CloudHttpClient http, CloudInstanceInfo info) {
        this.http = Objects.requireNonNull(http); this.info = Objects.requireNonNull(info);
        if (!info.supports("projectile_snapshot_v1")) throw new IllegalArgumentException("Projectile snapshots unsupported");
    }
    public CompletableFuture<Lease> publish(EntityDisplayContext context, ProjectileAppearanceSnapshot snapshot) {
        if (!context.worldEpoch().equals(snapshot.worldEpoch()) || !context.dimensionId().equals(snapshot.dimensionId())
                || !context.instanceId().equals(snapshot.selection().instanceId()) || !context.originSha256().equals(snapshot.selection().originSha256())) throw new IllegalArgumentException("Snapshot context mismatch");
        if (snapshot.variables().size() > info.maxVisualVariables()) throw new IllegalArgumentException("Firing variable budget exceeded");
        String text = toJson(snapshot).toString();
        if (text.getBytes(StandardCharsets.UTF_8).length > info.maxVisualStateBytes()) throw new IllegalArgumentException("Firing snapshot budget exceeded");
        return http.visualJson("PUT", path(context), text, responseBudget()).thenApply(response->{var lease=parseLease(response);if(!snapshot.equals(lease.snapshot()))throw new IllegalArgumentException("Unexpected firing acknowledgement");return lease;});
    }
    public CompletableFuture<Map<UUID, Lease>> query(EntityDisplayContext context, Collection<UUID> uuids) {
        JsonObject request = CloudVisualQueryBudget.request(context,uuids,info);
        return http.visualJson("POST", path(context) + "/query", request.toString(), responseBudget()).thenApply(text -> {
            JsonObject root=JsonParser.parseString(text).getAsJsonObject();fields(root,"entries");
            JsonArray entries=root.getAsJsonArray("entries");
            if (entries.size() > uuids.size()) throw new IllegalArgumentException("Unexpected snapshot entries");
            Map<UUID, Lease> result = new HashMap<>();
            for (JsonElement entry : entries) {
                Lease lease = parseLease(entry.toString()); UUID id = lease.snapshot().entityUuid();
                if (!uuids.contains(id) || !lease.snapshot().worldEpoch().equals(context.worldEpoch()) || !lease.snapshot().dimensionId().equals(context.dimensionId())
                        || result.put(id, lease) != null) throw new IllegalArgumentException("Unexpected projectile UUID or world");
            }
            return Map.copyOf(result);
        });
    }
    public CompletableFuture<Lease> renew(EntityDisplayContext context, Lease lease) {
        checkContext(context,lease);
        JsonObject request = location(context); request.addProperty("event_id", lease.snapshot().eventId());
        request.addProperty("expected_revision", lease.revision());
        return http.visualJson("PUT", path(context) + "/" + lease.snapshot().entityUuid() + "/lease",request.toString(),responseBudget()).thenApply(response->{var next=parseLease(response);if(!lease.snapshot().equals(next.snapshot())||next.revision()<=lease.revision())throw new IllegalArgumentException("Unexpected renewal acknowledgement");return next;});
    }
    public CompletableFuture<Lease> withdraw(EntityDisplayContext context,Lease lease){
        checkContext(context,lease);
        JsonObject request=location(context);request.addProperty("event_id",lease.snapshot().eventId());request.addProperty("expected_revision",lease.revision());
        return http.visualJson("DELETE",path(context)+"/"+lease.snapshot().entityUuid()+"/lease",request.toString(),responseBudget()).thenApply(response->{var next=parseLease(response);if(!lease.snapshot().equals(next.snapshot())||next.revision()<=lease.revision())throw new IllegalArgumentException("Unexpected withdrawal acknowledgement");return next;});
    }
    private String path(EntityDisplayContext context) {
        if (!http.instance().instanceId().equals(context.instanceId())||!CloudAssetCache.sha256(http.instance().origin().toString().getBytes(StandardCharsets.UTF_8)).equals(context.originSha256())) throw new IllegalArgumentException("Cloud instance mismatch");
        return "/v1/scopes/" + CloudScopeClient.segment(context.scopeId()) + "/projectiles";
    }
    private static void checkContext(EntityDisplayContext context,Lease lease){
        var snapshot=lease.snapshot();
        if(!context.worldEpoch().equals(snapshot.worldEpoch())||!context.dimensionId().equals(snapshot.dimensionId())
                ||!context.instanceId().equals(snapshot.selection().instanceId())||!context.originSha256().equals(snapshot.selection().originSha256()))
            throw new IllegalArgumentException("Projectile lease context mismatch");
    }
    private int responseBudget() {
        long perEntry = Math.min(info.maxVisualStateBytes(), 1048576L) + 1024;
        return (int) Math.min(16L * 1024 * 1024, perEntry * info.maxEntityQueryCount() + 4096);
    }
    private static JsonObject location(EntityDisplayContext context) {
        JsonObject result = new JsonObject(); result.addProperty("world_epoch",context.worldEpoch());result.addProperty("dimension_id",context.dimensionId());return result;
    }
    static JsonObject toJson(ProjectileAppearanceSnapshot value) {
        JsonObject result = new JsonObject(); result.addProperty("world_epoch",value.worldEpoch());result.addProperty("dimension_id",value.dimensionId());
        result.addProperty("entity_uuid",value.entityUuid().toString());result.addProperty("entity_kind",value.entityKind());
        result.addProperty("source_identity_id",value.sourceIdentityId());result.addProperty("source_entity_uuid",value.sourceEntityUuid().toString());result.addProperty("event_id",value.eventId());
        JsonObject resource = new JsonObject();var selection=value.selection();
        resource.addProperty("asset_id",selection.ref().assetId());resource.addProperty("asset_revision",selection.ref().revision());
        resource.addProperty("raw_sha256",selection.ref().rawSha256());resource.addProperty("texture_id",selection.textureId());resource.addProperty("format",selection.format());
        result.add("resource",resource);result.addProperty("projectile_bundle_key",value.projectileBundleKey());
        JsonObject variables=new JsonObject();value.variables().forEach(variables::addProperty);result.add("variables",variables);
        if(value.firingItemId()==null)result.add("firing_item_id",JsonNull.INSTANCE);else result.addProperty("firing_item_id",value.firingItemId());
        return result;
    }
    Lease parseLease(String text) {
        JsonObject root=JsonParser.parseString(text).getAsJsonObject();JsonObject s=root.getAsJsonObject("snapshot"),r=s.getAsJsonObject("resource");
        fields(root,"snapshot","lease_revision","received_at_unix_ms","server_time_unix_ms","expires_at_unix_ms","absolute_expires_at_unix_ms");
        fields(s,"world_epoch","dimension_id","entity_uuid","entity_kind","source_identity_id","source_entity_uuid","event_id","resource","projectile_bundle_key","variables","firing_item_id");
        fields(r,"asset_id","asset_revision","raw_sha256","format","texture_id");
        if (s.toString().getBytes(StandardCharsets.UTF_8).length > info.maxVisualStateBytes()) throw new IllegalArgumentException("Snapshot exceeds negotiated budget");
        Map<String,Float> variables=new HashMap<>();JsonObject values=s.getAsJsonObject("variables");
        if(values.size()>info.maxVisualVariables())throw new IllegalArgumentException("Too many firing variables");
        for(var entry:values.entrySet()) {if(!entry.getValue().isJsonPrimitive()||!entry.getValue().getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Invalid firing variable");variables.put(entry.getKey(),entry.getValue().getAsFloat());}
        String origin=CloudAssetCache.sha256(http.instance().origin().toString().getBytes(StandardCharsets.UTF_8));
        var snapshot=new ProjectileAppearanceSnapshot(string(s,"world_epoch"),string(s,"dimension_id"),UUID.fromString(string(s,"entity_uuid")),string(s,"entity_kind"),
                string(s,"source_identity_id"),UUID.fromString(string(s,"source_entity_uuid")),string(s,"event_id"),
                new CloudPlayerSelection(http.instance().instanceId(),origin,new CloudAssetRef(string(r,"asset_id"),integer(r,"asset_revision"),string(r,"raw_sha256")),string(r,"format"),string(r,"texture_id")),
                string(s,"projectile_bundle_key"),variables,s.get("firing_item_id").isJsonNull()?null:string(s,"firing_item_id"));
        long received=integer(root,"received_at_unix_ms"),server=integer(root,"server_time_unix_ms"),absolute=integer(root,"absolute_expires_at_unix_ms");
        if(received>server||absolute<received||absolute-received>604800000L)throw new IllegalArgumentException("Invalid projectile lifetime");
        return new Lease(snapshot,integer(root,"lease_revision"),server,integer(root,"expires_at_unix_ms"),absolute);
    }
    private static void fields(JsonObject object,String... names){
        Set<String> expected=Set.of(names);if(object==null||!object.keySet().equals(expected))throw new IllegalArgumentException("Unexpected projectile fields");
    }
    private static String string(JsonObject object,String key){
        JsonElement value=object.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Invalid projectile text");return value.getAsString();
    }
    private static long integer(JsonObject object,String key) {
        JsonElement value=object.get(key);
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Invalid lease integer");
        long result=value.getAsBigDecimal().longValueExact();if(result<0||result>=9007199254740991L)throw new IllegalArgumentException("Invalid lease integer");return result;
    }
}
