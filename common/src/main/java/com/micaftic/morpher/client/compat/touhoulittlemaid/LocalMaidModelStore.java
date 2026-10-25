package com.micaftic.morpher.client.compat.touhoulittlemaid;

import com.google.gson.JsonObject;
import com.micaftic.morpher.core.model.CloudAssetIdentity;
import com.micaftic.morpher.core.storage.LocalJsonDocumentStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** Client preferences only; no model bytes or Cloud credentials are stored. */
public final class LocalMaidModelStore {
    public record Selection(String modelId, String textureId) {
        public Selection {
            modelId = modelId == null || modelId.isBlank() ? "default" : modelId;
            textureId = textureId == null || textureId.isBlank() ? "default" : textureId;
            if (CloudAssetIdentity.isRuntimeModelId(modelId))
                throw new IllegalArgumentException("A Cloud model is not a local maid selection");
        }
    }

    private final Path file;
    private JsonObject cached;
    public LocalMaidModelStore(Path file) { this.file = Objects.requireNonNull(file); }
    private JsonObject root() throws IOException {
        if (cached == null) cached = LocalJsonDocumentStore.readObject(file);
        return cached;
    }
    public synchronized Selection load(String world, UUID maid) throws IOException {
        try {
            var worlds = root().getAsJsonObject(world);
            if (worlds == null || !worlds.has(maid.toString())) return null;
            var row = worlds.getAsJsonObject(maid.toString());
            return new Selection(row.get("model").getAsString(), row.get("texture").getAsString());
        } catch (RuntimeException error) { throw new IOException("Invalid local maid preferences; original retained", error); }
    }
    public synchronized void save(String world, UUID maid, Selection selection) throws IOException {
        JsonObject next = root().deepCopy();
        JsonObject rows = next.has(world) ? next.getAsJsonObject(world) : new JsonObject();
        JsonObject row = new JsonObject();
        row.addProperty("model", selection.modelId());
        row.addProperty("texture", selection.textureId());
        rows.add(maid.toString(), row);
        next.add(world, rows);
        LocalJsonDocumentStore.saveObject(file, next);
        cached = next;
    }
    public synchronized void remove(String world, UUID maid) throws IOException {
        JsonObject next = root().deepCopy();
        if (!next.has(world)) return;
        JsonObject rows = next.getAsJsonObject(world);
        if (rows.remove(maid.toString()) == null) return;
        if (rows.size() == 0) next.remove(world);
        LocalJsonDocumentStore.saveObject(file, next);
        cached = next;
    }
}
