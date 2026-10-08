package com.micaftic.morpher.client;

import com.micaftic.morpher.core.storage.LocalDisplayPreferencesStore;
import com.micaftic.morpher.core.storage.LocalModelDefaultsStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads only the client preferences that used to live in the retired server TOML. */
public final class LegacyServerConfigMigration {
    private static final List<String> LEGACY_FILES = List.of(
            "sparkle_morpher-server.toml",
            "sparkle_morpher-synced.toml",
            "sparkle_morpher-common.toml",
            "sparkle_morpher-client.toml",
            "yes_steve_model-server.toml",
            "yes_steve_model-synced.toml",
            "yes_steve_model-common.toml",
            "yes_steve_model-client.toml");

    public record Preferences(LocalModelDefaultsStore.Defaults defaults,
                              LocalDisplayPreferencesStore.Preferences display) { }

    private LegacyServerConfigMigration() { }

    public static Preferences read(Path configDirectory) throws IOException {
        Path source = LEGACY_FILES.stream().map(configDirectory::resolve).filter(Files::isRegularFile).findFirst().orElse(null);
        if (source == null) return fallback();

        try {
            Map<String, String> config = readRelevantValues(source);
            String modelId = string(config, "DefaultModelId", "default");
            String textureId = string(config, "DefaultModelTexture", "default");
            Set<String> hidden = Set.copyOf(strings(config.get("ClientNotDisplayModels")));
            int soundMode = integer(config, "server_scheduler.AcceptSoundFX", 0);
            return new Preferences(new LocalModelDefaultsStore.Defaults(modelId, textureId),
                    new LocalDisplayPreferencesStore.Preferences(hidden, soundMode));
        } catch (RuntimeException | IOException invalid) {
            throw new IOException("Legacy client preferences could not be read; original TOML retained at " + source, invalid);
        }
    }

    public static Preferences readIfNeeded(Path configDirectory, Path localDataDirectory) throws IOException {
        if (Files.exists(localDataDirectory.resolve("local-model-defaults.json"))
                && Files.exists(localDataDirectory.resolve("local-display-preferences.json"))) return fallback();
        return read(configDirectory);
    }

    private static Preferences fallback() {
        return new Preferences(LocalModelDefaultsStore.FALLBACK, LocalDisplayPreferencesStore.DEFAULT);
    }

    private static Map<String, String> readRelevantValues(Path source) throws IOException {
        Map<String, String> values = new HashMap<>();
        String section = "";
        for (String rawLine : Files.readAllLines(source, StandardCharsets.UTF_8)) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                if (section.isEmpty() || section.contains("[") || section.contains("]"))
                    throw new IOException("Invalid TOML section");
                continue;
            }
            int equals = line.indexOf('=');
            if (equals <= 0) throw new IOException("Invalid TOML assignment");
            String key = line.substring(0, equals).trim();
            String qualifiedKey = section.isEmpty() ? key : section + "." + key;
            if (Set.of("DefaultModelId", "DefaultModelTexture", "ClientNotDisplayModels", "server_scheduler.AcceptSoundFX")
                    .contains(qualifiedKey) && values.putIfAbsent(qualifiedKey, line.substring(equals + 1).trim()) != null)
                throw new IOException("Duplicate legacy preference: " + qualifiedKey);
        }
        return values;
    }

    private static String stripComment(String line) {
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (escaped) { escaped = false; continue; }
            if (quoted && character == '\\') { escaped = true; continue; }
            if (character == '"') quoted = !quoted;
            if (character == '#' && !quoted) return line.substring(0, index);
        }
        return line;
    }

    private static String string(Map<String, String> config, String key, String fallback) throws IOException {
        String value = config.get(key);
        if (value == null) return fallback;
        if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"')
            throw new IOException("Invalid string preference: " + key);
        return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static int integer(Map<String, String> config, String key, int fallback) throws IOException {
        String value = config.get(key);
        if (value == null) return fallback;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException invalid) { throw new IOException("Invalid integer preference: " + key, invalid); }
    }

    private static List<String> strings(String value) throws IOException {
        if (value == null || "[]".equals(value)) return List.of();
        if (value.length() < 2 || value.charAt(0) != '[' || value.charAt(value.length() - 1) != ']')
            throw new IOException("Invalid legacy hidden model list");
        List<String> result = new ArrayList<>();
        String body = value.substring(1, value.length() - 1).trim();
        if (body.isEmpty()) return List.of();
        for (String item : body.split(",")) {
            String trimmed = item.trim();
            if (trimmed.length() < 2 || trimmed.charAt(0) != '"' || trimmed.charAt(trimmed.length() - 1) != '"')
                throw new IOException("Invalid legacy hidden model list");
            result.add(trimmed.substring(1, trimmed.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        return result;
    }
}
