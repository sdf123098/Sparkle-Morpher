package com.micaftic.morpher.cloud.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micaftic.morpher.YesSteveModel;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Local, bounded index for Cloud assets explicitly used or favorited by the player. */
public final class CloudModelSelectionStore {
    private static final Path FILE = Platform.getConfigFolder().resolve(YesSteveModel.MOD_ID).resolve("cloud_model_selection.json");
    private static final int MAX_RECENT = 100;
    private static final int MAX_FAVORITES = 500;
    private static final Object LOCK = new Object();

    private CloudModelSelectionStore() {}

    public static List<CloudAssetSummary> recent(String instanceId) {
        return read(instanceId, false);
    }

    public static List<CloudAssetSummary> favorites(String instanceId) {
        return read(instanceId, true);
    }

    public static boolean isFavorite(String instanceId, String assetId) {
        synchronized (LOCK) {
            JsonObject root = readRoot();
            JsonArray entries = entries(root, instanceId);
            for (var element : entries) {
                if (element.isJsonObject() && assetId.equals(text(element.getAsJsonObject(), "asset_id"))) {
                    return element.getAsJsonObject().has("favorite") && element.getAsJsonObject().get("favorite").getAsBoolean();
                }
            }
            return false;
        }
    }

    public static void recordApplied(String instanceId, CloudAssetSummary summary) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(summary, "summary");
        synchronized (LOCK) {
            JsonObject root = readRoot();
            JsonArray source = entries(root, instanceId);
            JsonArray next = new JsonArray();
            JsonObject updated = toJson(summary, true, System.currentTimeMillis());
            for (var element : source) {
                if (!element.isJsonObject() || !summary.ref().assetId().equals(text(element.getAsJsonObject(), "asset_id"))) next.add(element);
                else updated.addProperty("favorite", element.getAsJsonObject().has("favorite") && element.getAsJsonObject().get("favorite").getAsBoolean());
            }
            next.add(updated);
            trim(next, MAX_RECENT);
            putEntries(root, instanceId, next);
            writeRoot(root);
        }
    }

    public static boolean toggleFavorite(String instanceId, CloudAssetSummary summary) {
        Objects.requireNonNull(summary, "summary");
        synchronized (LOCK) {
            JsonObject root = readRoot();
            JsonArray source = entries(root, instanceId);
            JsonArray next = new JsonArray();
            boolean favorite = false;
            boolean found = false;
            for (var element : source) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                if (summary.ref().assetId().equals(text(item, "asset_id"))) {
                    found = true;
                    favorite = !(item.has("favorite") && item.get("favorite").getAsBoolean());
                    item = toJson(summary, favorite, item.has("last_used_at") ? item.get("last_used_at").getAsLong() : 0L);
                }
                next.add(item);
            }
            if (!found) {
                favorite = true;
                next.add(toJson(summary, true, System.currentTimeMillis()));
            }
            trimFavorites(next, MAX_FAVORITES);
            putEntries(root, instanceId, next);
            writeRoot(root);
            return favorite;
        }
    }

    private static List<CloudAssetSummary> read(String instanceId, boolean favoritesOnly) {
        synchronized (LOCK) {
            List<CloudAssetSummary> result = new ArrayList<>();
            JsonArray source = entries(readRoot(), instanceId);
            for (int i = source.size() - 1; i >= 0; i--) {
                var element = source.get(i);
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                if (favoritesOnly && !(item.has("favorite") && item.get("favorite").getAsBoolean())) continue;
                try { result.add(fromJson(item)); } catch (RuntimeException ignored) { }
            }
            return List.copyOf(result);
        }
    }

    private static JsonArray entries(JsonObject root, String instanceId) {
        if (!root.has(instanceId) || !root.get(instanceId).isJsonArray()) return new JsonArray();
        return root.getAsJsonArray(instanceId);
    }

    private static void putEntries(JsonObject root, String instanceId, JsonArray values) { root.add(instanceId, values); }

    private static JsonObject toJson(CloudAssetSummary summary, boolean favorite, long lastUsedAt) {
        JsonObject out = new JsonObject();
        out.addProperty("asset_id", summary.ref().assetId());
        out.addProperty("revision", summary.ref().revision());
        out.addProperty("raw_sha256", summary.ref().rawSha256());
        out.addProperty("name", summary.name());
        out.addProperty("format", summary.format());
        out.addProperty("byte_length", summary.byteLength());
        out.addProperty("visibility", summary.visibility());
        out.addProperty("favorite", favorite);
        out.addProperty("last_used_at", lastUsedAt);
        return out;
    }

    private static CloudAssetSummary fromJson(JsonObject object) {
        return new CloudAssetSummary(new CloudAssetRef(text(object, "asset_id"), object.get("revision").getAsLong(), text(object, "raw_sha256")),
                text(object, "name"), text(object, "format"), object.get("byte_length").getAsLong(),
                object.has("visibility") ? text(object, "visibility") : "PRIVATE");
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static void trim(JsonArray values, int max) {
        while (values.size() > max) values.remove(0);
    }

    private static void trimFavorites(JsonArray values, int max) {
        while (countFavorites(values) > max) {
            for (int i = 0; i < values.size(); i++) {
                var item = values.get(i);
                if (item.isJsonObject() && item.getAsJsonObject().has("favorite") && item.getAsJsonObject().get("favorite").getAsBoolean()) {
                    item.getAsJsonObject().addProperty("favorite", false);
                    break;
                }
            }
        }
    }

    private static int countFavorites(JsonArray values) {
        int count = 0;
        for (var item : values) if (item.isJsonObject() && item.getAsJsonObject().has("favorite") && item.getAsJsonObject().get("favorite").getAsBoolean()) count++;
        return count;
    }

    private static JsonObject readRoot() {
        if (!Files.isRegularFile(FILE)) return new JsonObject();
        try { return JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (Exception e) { YesSteveModel.LOGGER.warn("[SM] Failed to load Cloud model selection store", e); return new JsonObject(); }
    }

    private static void writeRoot(JsonObject root) {
        try {
            Path parent = FILE.getParent();
            Files.createDirectories(parent);
            Path temp = parent.resolve(FILE.getFileName() + ".tmp");
            Files.writeString(temp, root.toString(), StandardCharsets.UTF_8);
            try { Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { YesSteveModel.LOGGER.warn("[SM] Failed to save Cloud model selection store", e); }
    }
}
