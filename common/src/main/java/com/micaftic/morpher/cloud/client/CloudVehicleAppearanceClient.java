package com.micaftic.morpher.cloud.client;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class CloudVehicleAppearanceClient {
    private final CloudHttpClient http;
    private final CloudInstanceInfo info;
    public CloudVehicleAppearanceClient(CloudHttpClient http, CloudInstanceInfo info) {
        this.http=Objects.requireNonNull(http); this.info=Objects.requireNonNull(info);
        if (!info.supports("vehicle_appearance_v1")) throw new IllegalArgumentException("Vehicle bindings unsupported");
    }
    public CompletableFuture<Map<UUID,CloudVehicleAppearance>> query(EntityDisplayContext context, Collection<UUID> uuids) {
        JsonObject request=CloudVisualQueryBudget.request(context,uuids,info);
        Set<UUID> requested=Set.copyOf(uuids);
        return http.visualJson("POST",path(context)+"/query",request.toString(),responseBudget()).thenApply(text->{
            JsonArray entries=JsonParser.parseString(text).getAsJsonObject().getAsJsonArray("entries");
            if(entries.size()>requested.size())throw new IllegalArgumentException("Too many vehicle entries");
            Map<UUID,CloudVehicleAppearance> result=new HashMap<>();
            for(JsonElement entry:entries){var parsed=parse(entry.getAsJsonObject());
                if(!requested.contains(parsed.entityUuid())||result.put(parsed.entityUuid(),parsed)!=null)throw new IllegalArgumentException("Unexpected vehicle UUID");}
            return Map.copyOf(result);
        });
    }
    public CompletableFuture<CloudVehicleAppearance> publish(EntityDisplayContext context,UUID entity,String kind,
            String target,long revision,CloudPlayerSelection selection,Map<String,Float> variables) {
        JsonObject request=location(context);request.addProperty("entity_kind",kind);request.addProperty("target_id",target);request.addProperty("expected_revision",revision);
        JsonObject vars=new JsonObject();variables.forEach(vars::addProperty);request.add("variables",vars);
        JsonElement resource=JsonNull.INSTANCE;
        if(selection!=null){JsonObject r=new JsonObject();r.addProperty("asset_id",selection.ref().assetId());r.addProperty("asset_revision",selection.ref().revision());
            r.addProperty("raw_sha256",selection.ref().rawSha256());r.addProperty("texture_id",selection.textureId());r.addProperty("format",selection.format());resource=r;}
        request.add("resource",resource);
        if(variables.size()>info.maxVisualVariables()||request.toString().getBytes(StandardCharsets.UTF_8).length>info.maxVisualStateBytes())throw new IllegalArgumentException("Vehicle state budget exceeded");
        return http.visualJson("PUT",path(context)+"/"+entity,request.toString(),responseBudget()).thenApply(text->parse(JsonParser.parseString(text).getAsJsonObject()));
    }
    CloudVehicleAppearance parse(JsonObject root) {
        JsonObject b=root.getAsJsonObject("binding");
        if(b.toString().getBytes(StandardCharsets.UTF_8).length>info.maxVisualStateBytes())throw new IllegalArgumentException("Vehicle state budget exceeded");
        CloudPlayerSelection selection=null;
        if(!b.get("resource").isJsonNull()){JsonObject r=b.getAsJsonObject("resource");selection=new CloudPlayerSelection(http.instance().instanceId(),origin(),
            new CloudAssetRef(string(r,"asset_id"),integer(r,"asset_revision"),string(r,"raw_sha256")),string(r,"format"),string(r,"texture_id"));}
        Map<String,Float> vars=new HashMap<>();JsonObject values=b.getAsJsonObject("variables");
        if(values.size()>info.maxVisualVariables())throw new IllegalArgumentException("Vehicle variable budget exceeded");
        for(var entry:values.entrySet()){JsonElement v=entry.getValue();if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Invalid vehicle variable");vars.put(entry.getKey(),v.getAsFloat());}
        return new CloudVehicleAppearance(UUID.fromString(string(root,"entity_uuid")),string(b,"world_epoch"),string(b,"dimension_id"),string(b,"entity_kind"),string(b,"target_id"),
                integer(root,"revision"),selection,vars,integer(root,"server_time_unix_ms"),integer(root,"expires_at_unix_ms"));
    }
    private String origin(){return CloudAssetCache.sha256(http.instance().origin().toString().getBytes(StandardCharsets.UTF_8));}
    private String path(EntityDisplayContext context) {
        if(!http.instance().instanceId().equals(context.instanceId())||!origin().equals(context.originSha256()))throw new IllegalArgumentException("Vehicle instance mismatch");
        return "/v1/scopes/"+CloudScopeClient.segment(context.scopeId())+"/vehicles";
    }
    private int responseBudget(){return (int)Math.min(16L*1024*1024,(info.maxVisualStateBytes()+1024)*info.maxEntityQueryCount()+4096);}
    private static JsonObject location(EntityDisplayContext context){JsonObject r=new JsonObject();r.addProperty("world_epoch",context.worldEpoch());r.addProperty("dimension_id",context.dimensionId());return r;}
    private static String string(JsonObject o,String key){JsonElement v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Invalid vehicle string");return v.getAsString();}
    private static long integer(JsonObject o,String key){JsonElement v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Invalid vehicle integer");long n=v.getAsBigDecimal().longValueExact();if(n<0||n>=9007199254740991L)throw new IllegalArgumentException("Invalid vehicle integer");return n;}
}
