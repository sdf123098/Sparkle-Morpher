package com.micaftic.morpher.cloud.client;

import com.google.gson.*;
import com.micaftic.morpher.core.display.PlayerDisplayState;
import java.util.*;

/** Explicit world context and receipt-relative lifetime for render-only player inputs. */
public record CloudPlayerDisplayState(String scopeId, String worldEpoch, String dimensionId,
        PlayerDisplayState state, long serverTimeUnixMs, long expiresAtUnixMs) {
    public CloudPlayerDisplayState {
        if (scopeId == null || !scopeId.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}")
                || worldEpoch == null || !worldEpoch.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}")
                || dimensionId == null || dimensionId.length() > 256
                || !dimensionId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid player display context");
        Objects.requireNonNull(state);
        if (serverTimeUnixMs < 0 || expiresAtUnixMs < 0) throw new IllegalArgumentException("Invalid display receipt");
    }

    public long deadlineNanos(long receivedAtNanos) {
        long remainingMs = expiresAtUnixMs <= serverTimeUnixMs ? 0 : Math.min(60_000, expiresAtUnixMs - serverTimeUnixMs);
        return receivedAtNanos + remainingMs * 1_000_000;
    }

    public JsonObject toJson(long maxBytes, long maxVariables) {
        JsonObject body = new JsonObject();
        body.addProperty("scope_id", scopeId); body.addProperty("world_epoch", worldEpoch);
        body.addProperty("dimension_id", dimensionId);
        JsonObject value = new JsonObject();
        number(value, "experience_level", state.experienceLevel()); number(value, "health", state.health());
        number(value, "max_health", state.maxHealth()); number(value, "food_level", state.foodLevel());
        bool(value, "flying", state.flying()); bool(value, "shield_blocking", state.shieldBlocking());
        number(value, "strafe_input", state.strafeInput()); number(value, "vertical_input", state.verticalInput());
        number(value, "forward_input", state.forwardInput());
        if (state.effectAmplifiers() != null) {
            if (state.effectAmplifiers().size() > maxVariables) throw new IllegalArgumentException("display effect budget");
            JsonObject effects = new JsonObject(); state.effectAmplifiers().forEach(effects::addProperty);
            value.add("effect_amplifiers", effects);
        }
        body.add("state", value);
        if (body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes)
            throw new IllegalArgumentException("display byte budget");
        return body;
    }
    private static void number(JsonObject body, String key, Number value) { if (value != null) body.addProperty(key, value); }
    private static void bool(JsonObject body, String key, Boolean value) { if (value != null) body.addProperty(key, value); }

    public static CloudPlayerDisplayState fromJson(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        JsonObject body = element.getAsJsonObject();
        JsonObject value = body.getAsJsonObject("state");
        Set<String> fields = Set.of("experience_level", "health", "max_health", "food_level", "effect_amplifiers",
                "flying", "shield_blocking", "strafe_input", "vertical_input", "forward_input");
        if (!fields.containsAll(value.keySet())) throw new IllegalArgumentException("Unknown display field");
        Map<String, Integer> effects = null;
        if (present(value, "effect_amplifiers")) {
            effects = new LinkedHashMap<>();
            for (var entry : value.getAsJsonObject("effect_amplifiers").entrySet()) effects.put(entry.getKey(), integer(entry.getValue()));
        }
        return new CloudPlayerDisplayState(string(body, "scope_id"), string(body, "world_epoch"), string(body, "dimension_id"),
                new PlayerDisplayState(integer(value, "experience_level"), decimal(value, "health"), decimal(value, "max_health"),
                        integer(value, "food_level"), effects, bool(value, "flying"), decimal(value, "strafe_input"),
                        decimal(value, "vertical_input"), decimal(value, "forward_input"), bool(value, "shield_blocking")),
                longInteger(body.get("server_time_unix_ms")), longInteger(body.get("expires_at_unix_ms")));
    }
    private static boolean present(JsonObject o, String key) { return o.has(key) && !o.get(key).isJsonNull(); }
    private static String string(JsonObject o, String key) {
        JsonElement value = o.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key);
        return value.getAsString();
    }
    private static long longInteger(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("display integer");
        try { return value.getAsBigDecimal().longValueExact(); }
        catch (ArithmeticException invalid) { throw new IllegalArgumentException("display integer", invalid); }
    }
    private static Integer integer(JsonElement value) { return Math.toIntExact(longInteger(value)); }
    private static Integer integer(JsonObject o, String key) { return present(o, key) ? integer(o.get(key)) : null; }
    private static Float decimal(JsonObject o, String key) {
        if (!present(o, key)) return null;
        JsonElement value = o.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key);
        return value.getAsFloat();
    }
    private static Boolean bool(JsonObject o, String key) {
        if (!present(o, key)) return null;
        JsonElement value = o.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(key);
        return value.getAsBoolean();
    }
}
