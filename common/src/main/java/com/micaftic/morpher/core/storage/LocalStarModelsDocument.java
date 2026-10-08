package com.micaftic.morpher.core.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** File-level persistence contract for local model favorites. */
public final class LocalStarModelsDocument {
    private static final String MIGRATED = "_legacy_local_starred_models_migrated";
    private static final String MODELS = "models";

    private LocalStarModelsDocument() {}

    public static Set<String> load(Path file, Path legacyFile, String scope) throws IOException {
        return read(file, legacyFile, scope).models();
    }

    public static void save(Path file, Path legacyFile, String scope, Set<String> models) throws IOException {
        Snapshot snapshot = read(file, legacyFile, scope);
        JsonObject root = snapshot.root();
        JsonObject entry = new JsonObject();
        JsonArray array = new JsonArray();
        models.stream().filter(LocalStarModelsDocument::valid).sorted().forEach(array::add);
        entry.add(MODELS, array);
        root.add(scope, entry);
        LocalJsonDocumentStore.saveObject(file, root);
    }

    private static Snapshot read(Path file, Path legacyFile, String scope) throws IOException {
        if (scope == null || scope.isBlank() || !scope.contains("|") || scope.endsWith("|unknown")) {
            throw new IllegalArgumentException("A resolved server and player scope is required");
        }
        JsonObject root = LocalJsonDocumentStore.readObject(file);
        LinkedHashSet<String> models = readScope(root, scope);
        boolean migrated = root.has(MIGRATED) && root.get(MIGRATED).isJsonPrimitive()
                && root.getAsJsonPrimitive(MIGRATED).isBoolean()
                && root.getAsJsonPrimitive(MIGRATED).getAsBoolean();
        if (!migrated) {
            if (Files.exists(legacyFile)) {
                JsonArray oldModels = LocalJsonDocumentStore.readArray(legacyFile);
                for (JsonElement element : oldModels) {
                    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                        String model = element.getAsString();
                        if (valid(model)) models.add(model);
                    }
                }
            }
            root.add(scope, toEntry(models));
            root.addProperty(MIGRATED, true);
            LocalJsonDocumentStore.saveObject(file, root);
        }
        return new Snapshot(root, models);
    }

    private static LinkedHashSet<String> readScope(JsonObject root, String scope) {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        if (!root.has(scope) || !root.get(scope).isJsonObject()) return models;
        JsonObject entry = root.getAsJsonObject(scope);
        if (!entry.has(MODELS) || !entry.get(MODELS).isJsonArray()) return models;
        for (JsonElement element : entry.getAsJsonArray(MODELS)) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String model = element.getAsString();
                if (valid(model)) models.add(model);
            }
        }
        return models;
    }

    private static JsonObject toEntry(Set<String> models) {
        JsonObject entry = new JsonObject();
        JsonArray array = new JsonArray();
        (models == null ? Set.<String>of() : models).stream()
                .filter(LocalStarModelsDocument::valid).sorted().forEach(array::add);
        entry.add(MODELS, array);
        return entry;
    }

    private static boolean valid(String model) {
        return model != null && !model.isBlank();
    }

    private record Snapshot(JsonObject root, LinkedHashSet<String> models) {}
}
