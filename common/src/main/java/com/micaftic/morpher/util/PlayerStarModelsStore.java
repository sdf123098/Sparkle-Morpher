package com.micaftic.morpher.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.StarModelsCapability;
import com.micaftic.morpher.model.ServerModelManager;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class PlayerStarModelsStore {
    private static final Path FILE = ServerModelManager.FOLDER.resolve("player_star_models.json");
    private static final String PLAYERS = "players";
    private static final String MODELS = "models";
    private static final String NAME = "name";
    private static final String UPDATED_AT = "updated_at";

    private static JsonObject root;
    private static boolean loaded;

    private PlayerStarModelsStore() {
    }

    public static boolean restore(ServerPlayer player) {
        boolean[] restored = {false};
        StarModelsCapability.get(player).ifPresent(cap -> restored[0] = restore(player, cap));
        return restored[0];
    }

    public static synchronized boolean restore(ServerPlayer player, StarModelsCapability cap) {
        JsonObject entry = getEntry(player.getUUID());
        if (entry == null || !entry.has(MODELS) || !entry.get(MODELS).isJsonArray()) {
            return false;
        }
        LinkedHashSet<String> models = new LinkedHashSet<>();
        for (JsonElement element : entry.getAsJsonArray(MODELS)) {
            if (element.isJsonPrimitive()) {
                String modelId = element.getAsString();
                if (isValidModelId(modelId)) {
                    models.add(modelId);
                }
            }
        }
        cap.setStarModels(models);
        return true;
    }

    public static synchronized void save(ServerPlayer player, StarModelsCapability cap) {
        Set<String> models = cap.getStarModels();
        if (models == null || models.isEmpty()) {
            remove(player.getUUID());
            return;
        }

        JsonObject entry = new JsonObject();
        entry.addProperty(NAME, player.getGameProfile().getName());
        JsonArray array = new JsonArray();
        models.stream()
                .filter(PlayerStarModelsStore::isValidModelId)
                .sorted()
                .forEach(array::add);
        entry.add(MODELS, array);
        entry.addProperty(UPDATED_AT, System.currentTimeMillis());
        getPlayers().add(player.getUUID().toString(), entry);
        save();
    }

    private static boolean isValidModelId(@Nullable String modelId) {
        return modelId != null && !modelId.isBlank();
    }

    @Nullable
    private static JsonObject getEntry(UUID uuid) {
        JsonElement element = getPlayers().get(uuid.toString());
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    private static void remove(UUID uuid) {
        if (getPlayers().remove(uuid.toString()) != null) {
            save();
        }
    }

    private static JsonObject getPlayers() {
        JsonObject data = getRoot();
        JsonElement element = data.get(PLAYERS);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        JsonObject players = new JsonObject();
        data.add(PLAYERS, players);
        return players;
    }

    private static JsonObject getRoot() {
        if (loaded) {
            return root;
        }
        loaded = true;
        root = new JsonObject();
        root.addProperty("version", 1);
        root.add(PLAYERS, new JsonObject());

        if (!Files.isRegularFile(FILE)) {
            return root;
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8));
            if (parsed != null && parsed.isJsonObject()) {
                root = parsed.getAsJsonObject();
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("Failed to read persisted favorite models: {}", FILE, e);
        }
        getPlayers();
        return root;
    }

    private static void save() {
        Path temp = null;
        try {
            Files.createDirectories(FILE.getParent());
            temp = Files.createTempFile(FILE.getParent(), FILE.getFileName().toString(), ".tmp");
            Files.writeString(temp, YesSteveModel.GSON.toJson(getRoot()), StandardCharsets.UTF_8);
            try {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;
        } catch (IOException e) {
            YesSteveModel.LOGGER.error("Failed to save persisted favorite models: {}", FILE, e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
