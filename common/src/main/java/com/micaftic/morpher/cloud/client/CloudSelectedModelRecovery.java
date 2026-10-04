package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.core.model.CloudAssetIdentity;
import com.micaftic.morpher.util.LocalModelSelectionStore;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Restores a selected Cloud model when its local import is missing on world re-entry. */
final class CloudSelectedModelRecovery {
    private static final long RETRY_MILLIS = 5_000L;
    private static boolean inFlight;
    private static long nextAttemptAt;

    private CloudSelectedModelRecovery() {}

    static void tick(CloudClientRuntime.RuntimeState runtime) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || inFlight) return;
        long now = System.currentTimeMillis();
        if (now < nextAttemptAt) return;
        nextAttemptAt = now + RETRY_MILLIS;

        Pair<String, String> saved = LocalModelSelectionStore.load();
        if (saved == null || !CloudAssetIdentity.isRuntimeModelId(saved.getLeft())) return;
        String modelId = saved.getLeft();
        if (ClientModelManager.getAvailableModelIds().contains(modelId)) return;

        String instanceId = runtime.instance().instanceId();
        CloudAssetSummary asset = findAsset(instanceId, modelId, CloudModelSelectionStore.recent(instanceId));
        if (asset == null) return;
        long worldGeneration;
        try {
            worldGeneration = CloudClientRuntime.currentWorldGeneration();
        } catch (IllegalStateException noWorldYet) {
            return;
        }

        inFlight = true;
        runtime.assetMaterialization().ensurePreviouslyApplied(asset.ref()).thenApplyAsync(path -> {
            try { return Files.readAllBytes(path); }
            catch (IOException failure) { throw new CompletionException(failure); }
        }).whenComplete((bytes, failure) -> client.execute(() -> {
            if (failure != null) {
                inFlight = false;
                YesSteveModel.LOGGER.warn("[SM] Failed to restore selected Cloud model {}", modelId, failure);
                return;
            }
            if (!stillSelected(runtime, worldGeneration, modelId)) {
                inFlight = false;
                return;
            }
            ClientModelManager.importLocalModel(modelId, CloudAssetImportName.fileName(asset), bytes, error -> {
                inFlight = false;
                if (error == null && stillSelected(runtime, worldGeneration, modelId)) {
                    ClientModelManager.restorePersistedModelSelection();
                }
            });
        }));
    }

    static CloudAssetSummary findAsset(String instanceId, String modelId, List<CloudAssetSummary> recent) {
        for (CloudAssetSummary asset : recent) {
            try {
                String candidate = new CloudAssetIdentity(instanceId, "catalog", asset.ref().assetId(),
                        Long.toString(asset.ref().revision()), asset.ref().rawSha256()).runtimeModelId();
                if (modelId.equals(candidate)) return asset;
            } catch (IllegalArgumentException ignored) {
                // Ignore an outdated or invalid saved summary and check the next one.
            }
        }
        return null;
    }

    private static boolean stillSelected(CloudClientRuntime.RuntimeState runtime, long worldGeneration, String modelId) {
        Pair<String, String> saved = LocalModelSelectionStore.load();
        return CloudClientRuntime.state() == runtime
                && CloudClientRuntime.isCurrentWorldGeneration(worldGeneration)
                && Minecraft.getInstance().player != null
                && saved != null && modelId.equals(saved.getLeft());
    }
}
