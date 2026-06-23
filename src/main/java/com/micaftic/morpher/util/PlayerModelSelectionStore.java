package com.micaftic.morpher.util;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.AuthModelsCapability;
import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.model.ServerModelManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micaftic.morpher.core.architectury.platform.Platform;
import net.minecraft.server.level.ServerPlayer;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

public final class PlayerModelSelectionStore {
    private static final Path FILE = Platform.getConfigFolder().resolve(YesSteveModel.MOD_ID).resolve("player_models.json");
    private static final String PLAYERS = "players";
    private static final String MODEL_ID = "model_id";
    private static final String SELECT_TEXTURE = "select_texture";
    private static final String DISABLED = "disabled";
    private static final String NAME = "name";
    private static final String UPDATED_AT = "updated_at";

    private static JsonObject root;
    private static boolean loaded;

    private PlayerModelSelectionStore() {
    }

    public static boolean restore(ServerPlayer player) {
        boolean[] restored = {false};
        ModelInfoCapability.get(player).ifPresent(modelInfoCap -> AuthModelsCapability.get(player).ifPresent(authModelsCap -> {
            restored[0] = restore(player, modelInfoCap, authModelsCap);
        }));
        return restored[0];
    }

    public static synchronized boolean restore(ServerPlayer player, ModelInfoCapability modelInfoCap, AuthModelsCapability authModelsCap) {
        if (ServerModelManager.getServerModelInfo().isEmpty()) {
            return false;
        }
        JsonObject entry = getEntry(player.getUUID());
        if (entry == null) {
            return false;
        }

        String modelId = getString(entry, MODEL_ID);
        String textureId = getString(entry, SELECT_TEXTURE);
        boolean disabled = getBoolean(entry, DISABLED, false);
        if (modelId == null || modelId.isBlank()) {
            removeSelection(player.getUUID());
            return false;
        }

        String resolvedTexture = ServerModelManager.resolveTextureOrDefault(modelId, textureId);
        String rejectReason = getRejectReason(modelId, resolvedTexture, authModelsCap);
        if (rejectReason != null) {
            NetworkOnlineDebugLog.warn("Dropping persisted player model: player={} uuid={} modelId={} texture={} reason={}",
                    player.getName().getString(), player.getUUID(), modelId, textureId, rejectReason);
            removeSelection(player.getUUID());
            return false;
        }

        if (!Objects.equals(modelId, modelInfoCap.getModelId()) || !Objects.equals(resolvedTexture, modelInfoCap.getSelectTexture())) {
            modelInfoCap.setModelAndTexture(modelId, resolvedTexture);
            NetworkOnlineDebugLog.info("Restored persisted player model: player={} uuid={} modelId={} texture={}",
                    player.getName().getString(), player.getUUID(), modelId, resolvedTexture);
        }
        if (modelInfoCap.isDisabled() != disabled) {
            modelInfoCap.setDisabled(disabled);
            NetworkOnlineDebugLog.info("Restored persisted player model disabled state: player={} uuid={} disabled={}",
                    player.getName().getString(), player.getUUID(), disabled);
        }
        return true;
    }

    public static synchronized void saveCurrentSelection(ServerPlayer player, ModelInfoCapability modelInfoCap) {
        String modelId = modelInfoCap.getModelId();
        String textureId = modelInfoCap.getSelectTexture();
        boolean disabled = modelInfoCap.isDisabled();
        if (modelId == null || modelId.isBlank() || textureId == null || textureId.isBlank() || (isDefaultSelection(modelId, textureId) && !disabled)) {
            removeSelection(player.getUUID());
            return;
        }

        JsonObject entry = new JsonObject();
        entry.addProperty(NAME, player.getName().getString());
        entry.addProperty(MODEL_ID, modelId);
        entry.addProperty(SELECT_TEXTURE, textureId);
        if (disabled) {
            entry.addProperty(DISABLED, true);
        }
        entry.addProperty(UPDATED_AT, System.currentTimeMillis());
        getPlayers().add(player.getUUID().toString(), entry);
        save();
        NetworkOnlineDebugLog.info("Saved persisted player model: player={} uuid={} modelId={} texture={} disabled={}",
                player.getName().getString(), player.getUUID(), modelId, textureId, disabled);
    }

    private static boolean isDefaultSelection(String modelId, String textureId) {
        Pair<String, String> defaultModelConfig = ServerModelManager.getDefaultModelConfig();
        return Objects.equals(modelId, defaultModelConfig.getLeft()) && Objects.equals(textureId, defaultModelConfig.getRight());
    }

    @Nullable
    private static String getRejectReason(String modelId, @Nullable String resolvedTexture, AuthModelsCapability authModelsCap) {
        if (!ServerModelManager.getServerModelInfo().containsKey(modelId)) {
            return "not_in_cache";
        }
        if (ServerModelManager.getAuthModels().contains(modelId) && !authModelsCap.containsModel(modelId)) {
            return "no_auth";
        }
        if (resolvedTexture == null) {
            return "texture_null";
        }
        return null;
    }

    @Nullable
    private static JsonObject getEntry(UUID uuid) {
        JsonElement element = getPlayers().get(uuid.toString());
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    @Nullable
    private static String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        return element.getAsString();
    }

    private static boolean getBoolean(JsonObject object, String key, boolean defaultValue) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            return defaultValue;
        }
        return element.getAsBoolean();
    }

    private static void removeSelection(UUID uuid) {
        JsonObject players = getPlayers();
        if (players.remove(uuid.toString()) != null) {
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
            YesSteveModel.LOGGER.warn("Failed to read persisted player models: {}", FILE, e);
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
            YesSteveModel.LOGGER.error("Failed to save persisted player models: {}", FILE, e);
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
