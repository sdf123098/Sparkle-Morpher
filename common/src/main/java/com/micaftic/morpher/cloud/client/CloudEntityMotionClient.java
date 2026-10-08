package com.micaftic.morpher.cloud.client;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Bounded HTTP transport. Entity identity and native lookup stay in the display adapter. */
public final class CloudEntityMotionClient {
    public record Entry(UUID entityUuid,CloudEntityMotion update,CloudPlayerSelection selection,long revision,long serverTimeMs,long expiresAtMs) {
        public Entry {
            Objects.requireNonNull(entityUuid);Objects.requireNonNull(update);selection=Objects.requireNonNull(selection).withoutMotion();
            if(revision<1||revision>=9007199254740991L||serverTimeMs<0||expiresAtMs<serverTimeMs||expiresAtMs-serverTimeMs>60000)
                throw new IllegalArgumentException("Invalid entity motion lease");
        }
        public long deadlineNanos(long receipt){return receipt+(expiresAtMs-serverTimeMs)*1000000L;}
    }
    private final CloudHttpClient http;
    private final CloudInstanceInfo info;
    public CloudEntityMotionClient(CloudHttpClient http,CloudInstanceInfo info){
        this.http=Objects.requireNonNull(http);this.info=Objects.requireNonNull(info);
        if(!info.supports("entity_motion_v1"))throw new IllegalArgumentException("Entity motion unsupported");
    }
    public CompletableFuture<Map<UUID,Entry>> query(EntityDisplayContext context,Collection<UUID> ids){
        var request=queryBody(context,ids);var requested=Set.copyOf(ids);
        return http.visualJson("POST",path(context)+"/query",request.toString(),responseBudget()).thenApply(text->{
            var entries=JsonParser.parseString(text).getAsJsonObject().getAsJsonArray("entries");
            if(entries.size()>requested.size())throw new IllegalArgumentException("Too many entity motion entries");
            Map<UUID,Entry> result=new HashMap<>();for(var raw:entries){var entry=parse(raw.getAsJsonObject());
                if(!requested.contains(entry.entityUuid())||!entry.update.worldEpoch().equals(context.worldEpoch())
                    ||!entry.update.dimensionId().equals(context.dimensionId())||result.put(entry.entityUuid(),entry)!=null)
                    throw new IllegalArgumentException("Unexpected entity motion context");}
            return Map.copyOf(result);
        });
    }
    public CompletableFuture<Map<UUID,Long>> revisions(EntityDisplayContext context,Collection<UUID> ids){
        var request=queryBody(context,ids);var requested=Set.copyOf(ids);
        return http.visualJson("POST",path(context)+"/revisions",request.toString(),65536).thenApply(text->{
            var entries=JsonParser.parseString(text).getAsJsonObject().getAsJsonArray("entries");
            if(entries.size()>requested.size())throw new IllegalArgumentException("Too many revision entries");
            Map<UUID,Long> result=new HashMap<>();for(var raw:entries){var entry=raw.getAsJsonObject();UUID id=UUID.fromString(string(entry,"entity_uuid"));
                if(!requested.contains(id)||result.put(id,integer(entry,"revision"))!=null)throw new IllegalArgumentException("Unexpected entity revision");}
            return Map.copyOf(result);
        });
    }
    public CompletableFuture<Entry> publish(EntityDisplayContext context,UUID id,CloudEntityMotion motion,long expectedRevision){
        if(!motion.worldEpoch().equals(context.worldEpoch())||!motion.dimensionId().equals(context.dimensionId()))throw new IllegalArgumentException("Motion context mismatch");
        var request=location(context);request.addProperty("entity_kind",motion.entityKind());request.addProperty("target_id",motion.targetId());
        request.addProperty("binding_revision",motion.bindingRevision());request.addProperty("appearance_revision",motion.appearanceRevision());request.addProperty("expected_revision",expectedRevision);
        request.add("motion",motion.motion().toJson());validateUpdate(request);
        return http.visualJson("PUT",path(context)+"/"+id,request.toString(),responseBudget()).thenApply(text->{
            var entry=parse(JsonParser.parseString(text).getAsJsonObject());
            if(!id.equals(entry.entityUuid())||!motion.equals(entry.update()))throw new IllegalArgumentException("Unexpected motion publication acknowledgement");return entry;
        });
    }
    public CompletableFuture<Long> withdraw(EntityDisplayContext context,UUID id,long revision){
        var request=location(context);request.addProperty("expected_revision",revision);integer(request,"expected_revision");
        return http.visualJson("DELETE",path(context)+"/"+id,request.toString(),65536)
            .thenApply(text->integer(JsonParser.parseString(text).getAsJsonObject(),"revision"));
    }
    Entry parse(JsonObject root){
        var update=root.getAsJsonObject("update");validateUpdate(update);var resource=root.getAsJsonObject("resource");
        var selection=new CloudPlayerSelection(http.instance().instanceId(),origin(),new CloudAssetRef(string(resource,"asset_id"),integer(resource,"asset_revision"),string(resource,"raw_sha256")),string(resource,"format"),string(resource,"texture_id"));
        var motion=new CloudEntityMotion(string(update,"world_epoch"),string(update,"dimension_id"),string(update,"entity_kind"),string(update,"target_id"),integer(update,"binding_revision"),integer(update,"appearance_revision"),CloudPlayerMotion.fromJson(update.get("motion")));
        return new Entry(UUID.fromString(string(root,"entity_uuid")),motion,selection,integer(root,"revision"),integer(root,"server_time_unix_ms"),integer(root,"expires_at_unix_ms"));
    }
    private void validateUpdate(JsonObject update){
        keys(update,"world_epoch","dimension_id","entity_kind","target_id","binding_revision","appearance_revision","expected_revision","motion");
        integer(update,"binding_revision");integer(update,"appearance_revision");integer(update,"expected_revision");
        if(update.toString().getBytes(StandardCharsets.UTF_8).length>info.maxVisualStateBytes())throw new IllegalArgumentException("Entity motion byte budget exceeded");
        JsonObject m=update.getAsJsonObject("motion");keys(m,"event_id","animation_key","started_at_unix_ms","roaming","expressions","controllers");
        string(m,"event_id");string(m,"animation_key");integer(m,"started_at_unix_ms");int variables=numbers(m.get("roaming"));
        if(m.has("expressions"))for(var raw:m.getAsJsonArray("expressions")){var e=raw.getAsJsonObject();keys(e,"event_id","started_at_unix_ms","expression","values");string(e,"event_id");string(e,"expression");integer(e,"started_at_unix_ms");for(var value:e.getAsJsonArray("values"))number(value);}
        if(m.has("controllers"))for(var raw:m.getAsJsonObject("controllers").entrySet()){if(raw.getKey().chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid motion controller name");var c=raw.getValue().getAsJsonObject();keys(c,"state","started_at_unix_ms","variables");string(c,"state");integer(c,"started_at_unix_ms");variables+=numbers(c.get("variables"));}
        if(variables>info.maxVisualVariables())throw new IllegalArgumentException("Entity motion variable budget exceeded");
        CloudPlayerMotion.fromJson(m).toJson();
    }
    private static int numbers(JsonElement values){if(values==null)return 0;var map=values.getAsJsonObject();map.entrySet().forEach(e->{if(e.getKey().chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid motion variable name");number(e.getValue());});return map.size();}
    private static void number(JsonElement value){if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber()||!Float.isFinite(value.getAsFloat()))throw new IllegalArgumentException("Invalid motion number");}
    private static void keys(JsonObject object,String... allowed){var names=Set.of(allowed);if(object.keySet().stream().anyMatch(key->!names.contains(key)))throw new IllegalArgumentException("Unknown motion field");}
    private static String string(JsonObject object,String key){var value=object.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Invalid motion string");String text=value.getAsString();if(text.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid motion text");return text;}
    private static long integer(JsonObject object,String key){var value=object.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Invalid motion integer");long n=value.getAsBigDecimal().longValueExact();if(n<0||n>=9007199254740991L)throw new IllegalArgumentException("Invalid motion integer");return n;}
    private JsonObject queryBody(EntityDisplayContext context,Collection<UUID> ids){return CloudVisualQueryBudget.request(context,ids,info);}
    private String origin(){return CloudAssetCache.sha256(http.instance().origin().toString().getBytes(StandardCharsets.UTF_8));}
    private String path(EntityDisplayContext context){if(!http.instance().instanceId().equals(context.instanceId())||!origin().equals(context.originSha256()))throw new IllegalArgumentException("Entity motion instance mismatch");return "/v1/scopes/"+CloudScopeClient.segment(context.scopeId())+"/entity-motion";}
    private int responseBudget(){return (int)Math.min(16L*1024*1024,(info.maxVisualStateBytes()+2048)*info.maxEntityQueryCount()+4096);}
    private static JsonObject location(EntityDisplayContext context){var request=new JsonObject();request.addProperty("world_epoch",context.worldEpoch());request.addProperty("dimension_id",context.dimensionId());return request;}
}
