package com.micaftic.morpher.cloud.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micaftic.morpher.cloud.CloudInstanceConfig;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Cross-platform registry for trusted Cloud instance origins.
 *
 * <p>Only non-secret instance metadata is persisted. Access/refresh tokens
 * belong to {@link CloudSession} and are intentionally absent from this
 * format.</p>
 */
public final class CloudInstanceRegistry {
    private final Path file;
    private List<CloudInstanceProfile> profiles = List.of();
    private String selectedInstanceId;

    public CloudInstanceRegistry(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    public synchronized void load() throws IOException {
        if (!Files.isRegularFile(file)) {
            profiles = List.of();
            selectedInstanceId = null;
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            List<CloudInstanceProfile> loaded = new ArrayList<>();
            JsonElement entries = root.get("instances");
            if (entries != null && entries.isJsonArray()) {
                for (JsonElement entry : entries.getAsJsonArray()) loaded.add(parseProfile(entry.getAsJsonObject()));
            }
            profiles = deduplicate(loaded);
            selectedInstanceId = nullableText(root, "selected_instance_id");
            if (selectedInstanceId != null && find(selectedInstanceId).isEmpty()) selectedInstanceId = null;
        } catch (RuntimeException failure) {
            throw new IOException("Malformed Cloud instance registry: " + file, failure);
        }
    }

    public synchronized void save() throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        if (selectedInstanceId == null) root.add("selected_instance_id", com.google.gson.JsonNull.INSTANCE);
        else root.addProperty("selected_instance_id", selectedInstanceId);
        JsonArray entries = new JsonArray();
        profiles.stream().sorted(Comparator.comparing(CloudInstanceProfile::instanceId)).forEach(profile -> entries.add(profileJson(profile)));
        root.add("instances", entries);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, root.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public synchronized void addOrReplace(CloudInstanceProfile profile) {
        Objects.requireNonNull(profile, "profile");
        List<CloudInstanceProfile> next = new ArrayList<>(profiles);
        next.removeIf(existing -> existing.instanceId().equals(profile.instanceId()));
        next.add(profile);
        profiles = deduplicate(next);
        if (selectedInstanceId == null) selectedInstanceId = profile.instanceId();
    }

    public synchronized boolean remove(String instanceId) {
        boolean removed = profiles.stream().anyMatch(profile -> profile.instanceId().equals(instanceId));
        if (!removed) return false;
        profiles = profiles.stream().filter(profile -> !profile.instanceId().equals(instanceId)).toList();
        if (instanceId.equals(selectedInstanceId)) selectedInstanceId = profiles.isEmpty() ? null : profiles.getFirst().instanceId();
        return true;
    }

    public synchronized void select(String instanceId) {
        if (find(instanceId).isEmpty()) throw new IllegalArgumentException("Unknown Cloud instance: " + instanceId);
        selectedInstanceId = instanceId;
    }

    public synchronized List<CloudInstanceProfile> profiles() {
        return List.copyOf(profiles);
    }

    public synchronized Optional<CloudInstanceProfile> selected() {
        return selectedInstanceId == null ? Optional.empty() : find(selectedInstanceId);
    }

    public synchronized Optional<CloudInstanceProfile> find(String instanceId) {
        return profiles.stream().filter(profile -> profile.instanceId().equals(instanceId)).findFirst();
    }

    private static List<CloudInstanceProfile> deduplicate(List<CloudInstanceProfile> entries) {
        java.util.LinkedHashMap<String, CloudInstanceProfile> unique = new java.util.LinkedHashMap<>();
        for (CloudInstanceProfile entry : entries) unique.put(entry.instanceId(), entry);
        return List.copyOf(unique.values());
    }

    private static CloudInstanceProfile parseProfile(JsonObject object) {
        return new CloudInstanceProfile(
                CloudInstanceConfig.v1(required(object, "instance_id"), URI.create(required(object, "origin"))),
                required(object, "name"));
    }

    private static JsonObject profileJson(CloudInstanceProfile profile) {
        JsonObject object = new JsonObject();
        object.addProperty("instance_id", profile.instanceId());
        object.addProperty("origin", profile.instance().origin().toString());
        object.addProperty("name", profile.name());
        object.addProperty("protocol", profile.instance().protocolVersion());
        return object;
    }

    private static String required(JsonObject object, String name) {
        String value = nullableText(object, name);
        if (value == null) throw new IllegalArgumentException("Missing Cloud instance field: " + name);
        return value;
    }

    private static String nullableText(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull() && !object.get(name).getAsString().isBlank()
                ? object.get(name).getAsString()
                : null;
    }

    public record CloudInstanceProfile(CloudInstanceConfig instance, String name) {
        public CloudInstanceProfile {
            Objects.requireNonNull(instance, "instance");
            if (name == null || name.isBlank() || name.length() > 256 || name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0) {
                throw new IllegalArgumentException("Cloud instance name must be a non-empty single-line value");
            }
        }

        public String instanceId() {
            return instance.instanceId();
        }
    }
}
