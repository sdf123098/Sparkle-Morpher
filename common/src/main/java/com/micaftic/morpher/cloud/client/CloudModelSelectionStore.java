package com.micaftic.morpher.cloud.client;

import com.google.gson.*;
import com.micaftic.morpher.core.storage.ModelStoragePaths;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Favorites are retained independently of bounded application history. */
public final class CloudModelSelectionStore {
    private static Index current;
    private CloudModelSelectionStore() {}
    private static synchronized Index index() {
        Path file = ModelStoragePaths.folder().resolve("cloud_model_selection.json");
        if (current == null || !current.file.equals(file)) current = new Index(file);
        return current;
    }
    public static List<CloudAssetSummary> recent(String key) { return index().read(key, false); }
    public static List<CloudAssetSummary> favorites(String key) { return index().read(key, true); }
    public static boolean isFavorite(String key, String id) { return index().isFavorite(key, id); }
    public static void recordApplied(String key, CloudAssetSummary asset) { index().recordApplied(key, asset); }
    public static boolean toggleFavorite(String key, CloudAssetSummary asset) {
        Index store = index();
        synchronized (store) { return store.setFavorite(key, asset, !store.isFavorite(key, asset.ref().assetId())); }
    }
    public static boolean setFavorite(String key, CloudAssetSummary asset, boolean value) { return index().setFavorite(key, asset, value); }
    public static void setFavorites(String key, List<CloudAssetSummary> assets, boolean value) { index().setFavorites(key, assets, value); }
    public static void refreshSummaries(String key, List<CloudAssetSummary> assets) { index().refreshSummaries(key, assets); }

    // Injectable file and cached reads; rendering a star never reads the disk again.
    static final class Index {
        private final Path file;
        private JsonObject cached;
        Index(Path file) { this.file = Objects.requireNonNull(file); }
        private JsonObject root() {
            if (cached == null) {
                try { cached = Files.exists(file) ? JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject(); }
                catch (IOException e) { throw new UncheckedIOException("Cannot read Cloud favorites", e); }
                catch (RuntimeException e) { throw new IllegalStateException("Invalid Cloud favorites file: " + file, e); }
            }
            return cached;
        }
        synchronized boolean isFavorite(String key, String id) {
            JsonObject item = find(entries(root(), key), id);
            return item != null && flag(item);
        }
        synchronized List<CloudAssetSummary> read(String key, boolean favoritesOnly) {
            List<JsonObject> items = new ArrayList<>();
            for (var element : entries(root(), key)) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                if (favoritesOnly ? flag(item) : usedAt(item) > 0) items.add(item);
            }
            Collections.reverse(items);
            if (!favoritesOnly) items.sort(Comparator.comparingLong(CloudModelSelectionStore::usedAt).reversed());
            List<CloudAssetSummary> result = new ArrayList<>();
            for (JsonObject item : items) {
                try { result.add(fromJson(!favoritesOnly && item.has("last_applied") && item.get("last_applied").isJsonObject() ? item.getAsJsonObject("last_applied") : item)); }
                catch (RuntimeException ignored) { /* A bad entry must not hide the rest. */ }
                if (!favoritesOnly && result.size() >= 100) break;
            }
            return List.copyOf(result);
        }
        synchronized void recordApplied(String key, CloudAssetSummary asset) {
            JsonObject next = root().deepCopy();
            JsonArray values = entries(next, key);
            JsonObject old = find(values, asset.ref().assetId());
            JsonObject updated = toJson(asset, old != null && flag(old), System.currentTimeMillis());
            if (old != null) {
                try { if (fromJson(old).ref().revision() >= asset.ref().revision()) updated = old.deepCopy(); }
                catch (RuntimeException ignored) { }
            }
            updated.addProperty("last_used_at", System.currentTimeMillis());
            JsonObject used = toJson(asset, false, 0);
            used.addProperty("name", text(updated, "name"));
            used.addProperty("visibility", text(updated, "visibility"));
            updated.add("last_applied", used);
            replace(values, updated); prune(values); next.add(key, values); save(next);
        }
        synchronized boolean setFavorite(String key, CloudAssetSummary asset, boolean value) {
            JsonObject next = root().deepCopy();
            JsonArray values = entries(next, key);
            JsonObject old = find(values, asset.ref().assetId());
            if (old == null && !value) return false;
            JsonObject updated = toJson(asset, value, old == null ? 0 : usedAt(old));
            if (old != null) {
                try { if (fromJson(old).ref().revision() >= asset.ref().revision()) { updated = old.deepCopy(); updated.addProperty("favorite", value); } }
                catch (RuntimeException ignored) { }
                if (usedAt(old) > 0) updated.add("last_applied", applied(old));
            }
            replace(values, updated); prune(values); next.add(key, values); save(next);
            return value;
        }
        synchronized void setFavorites(String key, List<CloudAssetSummary> assets, boolean value) {
            JsonObject next = root().deepCopy();
            JsonArray values = entries(next, key);
            for (CloudAssetSummary asset : assets) {
                JsonObject old = find(values, asset.ref().assetId());
                if (old == null && !value) continue;
                JsonObject updated = toJson(asset, value, old == null ? 0 : usedAt(old));
                if (old != null) {
                    try { if (fromJson(old).ref().revision() >= asset.ref().revision()) updated = old.deepCopy(); }
                    catch (RuntimeException ignored) { }
                    updated.addProperty("favorite", value);
                    if (usedAt(old) > 0) updated.add("last_applied", applied(old));
                }
                replace(values, updated);
            }
            prune(values); next.add(key, values); save(next);
        }
        synchronized void refreshSummaries(String key, List<CloudAssetSummary> assets) {
            JsonObject next = root().deepCopy();
            JsonArray values = entries(next, key);
            boolean changed = false;
            for (CloudAssetSummary asset : assets) {
                JsonObject old = find(values, asset.ref().assetId());
                if (old == null) continue;
                try { if (fromJson(old).ref().revision() > asset.ref().revision()) continue; }
                catch (RuntimeException ignored) { }
                JsonObject updated = toJson(asset, flag(old), usedAt(old));
                if (usedAt(old) > 0) {
                    JsonObject used = applied(old);
                    used.addProperty("name", asset.name()); used.addProperty("visibility", asset.visibility());
                    updated.add("last_applied", used);
                }
                if (!updated.equals(old)) { replace(values, updated); changed = true; }
            }
            if (changed) { next.add(key, values); save(next); }
        }
        private void save(JsonObject next) {
            Path temp = null;
            try {
                Path parent = file.toAbsolutePath().getParent(); Files.createDirectories(parent);
                temp = Files.createTempFile(parent, "cloud-selection-", ".tmp");
                Files.writeString(temp, next.toString(), StandardCharsets.UTF_8);
                try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
                cached = next;
            } catch (IOException e) { throw new UncheckedIOException("Cannot save Cloud favorites", e); }
            finally { if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { } }
        }
    }
    private static JsonObject applied(JsonObject item) {
        JsonObject copy = item.has("last_applied") && item.get("last_applied").isJsonObject() ? item.getAsJsonObject("last_applied").deepCopy() : item.deepCopy();
        copy.remove("last_applied"); return copy;
    }
    private static JsonArray entries(JsonObject root, String key) { return root.has(key) && root.get(key).isJsonArray() ? root.getAsJsonArray(key) : new JsonArray(); }
    private static JsonObject find(JsonArray values, String id) {
        for (var item : values) if (item.isJsonObject() && id.equals(text(item.getAsJsonObject(), "asset_id"))) return item.getAsJsonObject();
        return null;
    }
    private static void replace(JsonArray values, JsonObject updated) {
        for (int i = 0; i < values.size(); i++) if (values.get(i).isJsonObject() && text(updated, "asset_id").equals(text(values.get(i).getAsJsonObject(), "asset_id"))) { values.set(i, updated); return; }
        values.add(updated);
    }
    private static void prune(JsonArray values) {
        List<JsonObject> history = new ArrayList<>();
        for (var item : values) if (item.isJsonObject() && usedAt(item.getAsJsonObject()) > 0) history.add(item.getAsJsonObject());
        history.sort(Comparator.comparingLong(CloudModelSelectionStore::usedAt).reversed());
        for (int i = 100; i < history.size(); i++) { history.get(i).addProperty("last_used_at", 0); history.get(i).remove("last_applied"); }
        for (int i = values.size() - 1; i >= 0; i--) if (values.get(i).isJsonObject() && !flag(values.get(i).getAsJsonObject()) && usedAt(values.get(i).getAsJsonObject()) == 0) values.remove(i);
    }
    private static boolean flag(JsonObject item) { try { return item.has("favorite") && item.get("favorite").getAsBoolean(); } catch (RuntimeException ignored) { return false; } }
    private static long usedAt(JsonObject item) { try { return item.has("last_used_at") ? item.get("last_used_at").getAsLong() : 0; } catch (RuntimeException ignored) { return 0; } }
    private static JsonObject toJson(CloudAssetSummary asset, boolean favorite, long used) {
        JsonObject out = new JsonObject();
        out.addProperty("asset_id", asset.ref().assetId()); out.addProperty("revision", asset.ref().revision()); out.addProperty("raw_sha256", asset.ref().rawSha256());
        out.addProperty("name", asset.name()); out.addProperty("format", asset.format()); out.addProperty("byte_length", asset.byteLength()); out.addProperty("visibility", asset.visibility());
        out.addProperty("favorite", favorite); out.addProperty("last_used_at", used); return out;
    }
    private static CloudAssetSummary fromJson(JsonObject item) {
        return new CloudAssetSummary(new CloudAssetRef(text(item, "asset_id"), item.get("revision").getAsLong(), text(item, "raw_sha256")),
                text(item, "name"), text(item, "format"), item.get("byte_length").getAsLong(), item.has("visibility") ? text(item, "visibility") : "PRIVATE");
    }
    private static String text(JsonObject item, String key) { try { return item.has(key) && !item.get(key).isJsonNull() ? item.get(key).getAsString() : ""; } catch (RuntimeException ignored) { return ""; } }
}
