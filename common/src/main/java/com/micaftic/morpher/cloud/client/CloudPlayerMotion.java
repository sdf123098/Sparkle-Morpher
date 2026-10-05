package com.micaftic.morpher.cloud.client;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Model-bound player motion. The same wire format is used by every Cloud instance. */
public record CloudPlayerMotion(String eventId, String animationKey, long startedAtUnixMs,
        Map<String, Float> roaming, List<Expression> expressions, Map<String, Controller> controllers) {
    public record Expression(String eventId, long startedAtUnixMs, String expression, List<Float> values) {
        public Expression {
            text(eventId, 64, false); text(expression, 2048, true); timestamp(startedAtUnixMs);
            values = List.copyOf(values);
            if (values.size() > 16) throw new IllegalArgumentException("Too many expression arguments");
            values.forEach(CloudPlayerMotion::finite);
        }
    }
    public record Controller(String state, long startedAtUnixMs, Map<String, Float> variables) {
        public Controller { text(state, 128, true); timestamp(startedAtUnixMs); variables = numbers(variables, 64); }
    }
    public CloudPlayerMotion {
        text(eventId, 64, false); text(animationKey, 256, true); timestamp(startedAtUnixMs);
        roaming = numbers(roaming, 32); expressions = List.copyOf(expressions); controllers = Map.copyOf(controllers);
        if (expressions.size() > 16 || controllers.size() > 64) throw new IllegalArgumentException("Motion collection too large");
        controllers.keySet().forEach(name -> text(name, 128, false));
    }
    static void text(String value, int limit, boolean empty) {
        if (value == null || value.length() > limit || !empty && value.isBlank()) throw new IllegalArgumentException("Invalid motion text");
    }
    static void finite(Float value) {
        if (value == null || !Float.isFinite(value)) throw new IllegalArgumentException("Invalid motion number");
    }
    private static void timestamp(long value) {
        if (value < 0 || value > 9007199254740991L) throw new IllegalArgumentException("Invalid motion timestamp");
    }
    static Map<String, Float> numbers(Map<String, Float> values, int nameLimit) {
        if (values.size() > 64) throw new IllegalArgumentException("Too many motion variables");
        values.forEach((name, value) -> { text(name, nameLimit, false); finite(value); });
        return Map.copyOf(values);
    }
    public JsonObject toJson() {
        JsonObject root = new JsonObject(); root.addProperty("event_id", eventId);
        root.addProperty("animation_key", animationKey); root.addProperty("started_at_unix_ms", startedAtUnixMs);
        root.add("roaming", numberJson(roaming)); JsonArray events = new JsonArray();
        for (Expression event : expressions) {
            JsonObject row = new JsonObject(); row.addProperty("event_id", event.eventId());
            row.addProperty("started_at_unix_ms", event.startedAtUnixMs()); row.addProperty("expression", event.expression());
            JsonArray values = new JsonArray(); event.values().forEach(values::add); row.add("values", values); events.add(row);
        }
        root.add("expressions", events); JsonObject states = new JsonObject();
        controllers.forEach((name, state) -> {
            JsonObject row = new JsonObject(); row.addProperty("state", state.state());
            row.addProperty("started_at_unix_ms", state.startedAtUnixMs()); row.add("variables", numberJson(state.variables())); states.add(name, row);
        });
        root.add("controllers", states);
        if (root.toString().getBytes(StandardCharsets.UTF_8).length > 65536) throw new IllegalArgumentException("Motion payload exceeds 64 KiB");
        return root;
    }
    private static JsonObject numberJson(Map<String, Float> values) {
        JsonObject root = new JsonObject(); values.forEach(root::addProperty); return root;
    }
    private static Map<String, Float> parseNumbers(JsonElement value) {
        if (value == null) return Map.of(); Map<String, Float> result = new LinkedHashMap<>();
        value.getAsJsonObject().entrySet().forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsFloat())); return result;
    }
    public static CloudPlayerMotion fromJson(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (value.toString().getBytes(StandardCharsets.UTF_8).length > 65536) throw new IllegalArgumentException("Motion payload exceeds 64 KiB");
        JsonObject root = value.getAsJsonObject(); List<Expression> events = new ArrayList<>();
        if (root.has("expressions")) for (JsonElement item : root.getAsJsonArray("expressions")) {
            JsonObject row = item.getAsJsonObject(); List<Float> values = new ArrayList<>();
            for (JsonElement n : row.getAsJsonArray("values")) values.add(n.getAsFloat());
            events.add(new Expression(row.get("event_id").getAsString(), row.get("started_at_unix_ms").getAsLong(), row.get("expression").getAsString(), values));
        }
        Map<String, Controller> states = new LinkedHashMap<>();
        if (root.has("controllers")) root.getAsJsonObject("controllers").entrySet().forEach(entry -> {
            JsonObject row = entry.getValue().getAsJsonObject(); states.put(entry.getKey(), new Controller(row.get("state").getAsString(),
                    row.get("started_at_unix_ms").getAsLong(), parseNumbers(row.get("variables"))));
        });
        return new CloudPlayerMotion(root.get("event_id").getAsString(), root.get("animation_key").getAsString(),
                root.get("started_at_unix_ms").getAsLong(), parseNumbers(root.get("roaming")), events, states);
    }
}
