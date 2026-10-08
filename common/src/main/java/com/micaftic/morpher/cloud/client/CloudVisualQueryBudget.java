package com.micaftic.morpher.cloud.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.UUID;

/** UUID count and encoded request bytes are independent negotiated bounds. */
public final class CloudVisualQueryBudget {
    private CloudVisualQueryBudget() {}
    public static int batchLimit(EntityDisplayContext context, CloudInstanceInfo info) {
        long available = info.maxVisualStateBytes() - body(context, java.util.List.of()).toString().getBytes(StandardCharsets.UTF_8).length;
        int count = (int)Math.min(info.maxEntityQueryCount(), available / 39);
        if (count < 1) throw new IllegalArgumentException("Visual query byte budget cannot fit one UUID");
        return count;
    }
    public static JsonObject request(EntityDisplayContext context, Collection<UUID> ids, CloudInstanceInfo info) {
        if (ids.isEmpty() || ids.size() > info.maxEntityQueryCount()) throw new IllegalArgumentException("Visual query count exceeded");
        JsonObject request = body(context, ids);
        if (request.toString().getBytes(StandardCharsets.UTF_8).length > info.maxVisualStateBytes()) throw new IllegalArgumentException("Visual query byte budget exceeded");
        return request;
    }
    private static JsonObject body(EntityDisplayContext context, Collection<UUID> ids) {
        JsonObject request = new JsonObject(); request.addProperty("world_epoch", context.worldEpoch()); request.addProperty("dimension_id", context.dimensionId());
        JsonArray values = new JsonArray(); ids.forEach(id -> values.add(id.toString())); request.add("entity_uuids", values);
        return request;
    }
}
