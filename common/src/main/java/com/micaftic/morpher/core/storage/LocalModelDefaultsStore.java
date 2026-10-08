package com.micaftic.morpher.core.storage;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Client defaults survive the retirement of the former server configuration. */
public final class LocalModelDefaultsStore {
    public record Defaults(String modelId, String textureId) {
        public Defaults {
            if (modelId == null || modelId.isBlank() || modelId.length() > 512 || modelId.chars().anyMatch(Character::isISOControl)
                    || textureId == null || textureId.isBlank() || textureId.length() > 256 || textureId.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Invalid local model default");
        }
    }
    public static final Defaults FALLBACK = new Defaults("default", "default");
    private LocalModelDefaultsStore() {}
    public static Defaults loadOrSeed(Path file, Defaults legacy) throws IOException {
        if (Files.exists(file)) return read(file);
        save(file, legacy);
        return legacy;
    }
    public static Defaults read(Path file) throws IOException {
        if (Files.size(file) > 8192) throw new IOException("Local default settings are too large; retained at " + file);
        try {
            JsonObject object = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (object.size() != 3 || !object.has("schema_version") || !object.get("schema_version").isJsonPrimitive()
                    || !object.get("schema_version").getAsJsonPrimitive().isNumber() || object.get("schema_version").getAsBigDecimal().intValueExact() != 1)
                throw new IllegalArgumentException("schema version");
            for (String key : new String[]{"model_id", "texture_id"})
                if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.get(key).getAsJsonPrimitive().isString())
                    throw new IllegalArgumentException(key);
            return new Defaults(object.get("model_id").getAsString(), object.get("texture_id").getAsString());
        } catch (RuntimeException invalid) { throw new IOException("Unreadable local default settings; retained at " + file, invalid); }
    }
    public static void save(Path file, Defaults defaults) throws IOException {
        Path target = file.toAbsolutePath().normalize(); Files.createDirectories(target.getParent());
        JsonObject json = new JsonObject(); json.addProperty("schema_version", 1);
        json.addProperty("model_id", defaults.modelId()); json.addProperty("texture_id", defaults.textureId());
        Path temporary = Files.createTempFile(target.getParent(), "local-defaults-", ".tmp");
        try { Files.writeString(temporary, json.toString(), StandardCharsets.UTF_8); AtomicFileMover.moveWithRetry(temporary, target); }
        finally { Files.deleteIfExists(temporary); }
    }
}
