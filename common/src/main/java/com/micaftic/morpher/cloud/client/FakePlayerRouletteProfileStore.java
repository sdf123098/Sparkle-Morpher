package com.micaftic.morpher.cloud.client;

import com.google.gson.*;
import com.micaftic.morpher.core.storage.LocalJsonDocumentStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** User-authored preferences only: never model bytes, tokens, or received motion leases. */
public final class FakePlayerRouletteProfileStore {
    public record Key(String instance, String origin, String account, String scope, String world,
                      UUID entity, String target, String model) {
        public Key {
            for (String value : List.of(instance, origin, account, scope, world, target, model)) {
                if (value.isBlank() || value.length() > 512 || value.chars().anyMatch(Character::isISOControl))
                    throw new IllegalArgumentException("Invalid fake-player preference key");
            }
            Objects.requireNonNull(entity);
        }
        String digest() {
            JsonArray parts = new JsonArray();
            for (String value : List.of(instance, origin, account, scope, world, entity.toString(), target, model)) parts.add(value);
            return CloudAssetCache.sha256(parts.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    /** Null action means no explicit action choice; empty action is an explicit stop. */
    public record Profile(String action, Map<String, Float> variables, Map<String, String> settings) {
        public Profile {
            if (action != null) CloudPlayerMotion.text(action, 256, true);
            variables = CloudPlayerMotion.numbers(variables, 32);
            if (settings.size() > 64) throw new IllegalArgumentException("Too many saved controls");
            var ordered = new LinkedHashMap<String, String>();
            settings.forEach((control, expression) -> {
                CloudPlayerMotion.text(control, 2048, false);
                CloudPlayerMotion.text(expression, 2048, false);
                ordered.put(control, expression);
            });
            settings = Collections.unmodifiableMap(ordered);
            if (settings.toString().getBytes(StandardCharsets.UTF_8).length > 65536)
                throw new IllegalArgumentException("Saved controls exceed budget");
        }
        public static Profile empty() { return new Profile(null, Map.of(), Map.of()); }
        public Profile withAction(String value, Map<String, Float> numbers) { return new Profile(value, numbers, settings); }
        public Profile withSetting(String control, String expression, Map<String, Float> numbers) {
            var next = new LinkedHashMap<>(settings);
            next.remove(control); next.put(control, expression);
            return new Profile(action, numbers, next);
        }
    }
    private final Path file;
    private JsonObject cached;
    public FakePlayerRouletteProfileStore(Path file) { this.file = Objects.requireNonNull(file); }
    private JsonObject root() throws IOException {
        if (cached == null) cached = LocalJsonDocumentStore.readObject(file);
        return cached;
    }
    public synchronized Profile load(Key key) throws IOException {
        JsonElement value = root().get(key.digest());
        if (value == null) return null;
        try {
            JsonObject row = value.getAsJsonObject();
            var variables = new LinkedHashMap<String, Float>();
            row.getAsJsonObject("variables").entrySet().forEach(e -> variables.put(e.getKey(), e.getValue().getAsFloat()));
            var settings = new LinkedHashMap<String, String>();
            row.getAsJsonObject("settings").entrySet().forEach(e -> settings.put(e.getKey(), e.getValue().getAsString()));
            return new Profile(row.has("action") ? row.get("action").getAsString() : null, variables, settings);
        } catch (RuntimeException error) { throw new IOException("Invalid fake-player preferences; original retained", error); }
    }
    public synchronized void save(Key key, Profile profile) throws IOException {
        JsonObject next = root().deepCopy();
        if (!next.has(key.digest()) && next.size() >= 256) throw new IOException("Fake-player preference limit reached");
        JsonObject row = new JsonObject(), variables = new JsonObject(), settings = new JsonObject();
        if (profile.action() != null) row.addProperty("action", profile.action());
        profile.variables().forEach(variables::addProperty); profile.settings().forEach(settings::addProperty);
        row.add("variables", variables); row.add("settings", settings); next.add(key.digest(), row);
        LocalJsonDocumentStore.saveObject(file, next);
        cached = next;
    }
}
