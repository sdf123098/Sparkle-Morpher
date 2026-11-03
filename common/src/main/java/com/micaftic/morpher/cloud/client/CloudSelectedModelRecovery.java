package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.core.model.CloudAssetIdentity;
import com.micaftic.morpher.util.LocalModelSelectionStore;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Restores a selected Cloud model when its local import is missing on world re-entry. */
final class CloudSelectedModelRecovery {
    private static final long RETRY_MILLIS = 5_000L;
    private static boolean inFlight;
    private static long nextAttemptAt;
    private static long requestGeneration;
    private static String lastSavedModelId;
    private static String terminalPublicModelId;

    static void reset() {
        requestGeneration++;
        inFlight = false;
        nextAttemptAt = 0;
        lastSavedModelId = null;
        terminalPublicModelId = null;
    }

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
        if (!Objects.equals(lastSavedModelId, modelId)) {
            lastSavedModelId = modelId;
            terminalPublicModelId = null;
        }
        if (modelId.equals(terminalPublicModelId)) {
            if (CloudCdnPolicy.enabled()) return;
            terminalPublicModelId = null;
        }
        long worldGeneration;
        try {
            worldGeneration = CloudClientRuntime.currentWorldGeneration();
        } catch (IllegalStateException noWorldYet) {
            return;
        }

        String instanceId = runtime.instance().instanceId();
        CloudAssetSummary asset = findAsset(instanceId, modelId, CloudModelSelectionStore.recent(instanceId));
        if (asset == null) return;
        if (ClientModelManager.getAvailableModelIds().contains(modelId)) {
            restoreSelection(runtime, worldGeneration, saved);
            return;
        }
        inFlight = true;
        long request = ++requestGeneration;
        runtime.assetMaterialization().ensurePreviouslyApplied(asset.ref())
                .thenApply(CloudAssetClient.MemoryAsset::bytes)
                .whenComplete((bytes, failure) -> client.execute(() -> {
            if (request != requestGeneration) return;
            if (failure != null) {
                inFlight = false;
                if (terminalPublicCdnMiss(asset, failure, CloudCdnPolicy.enabled())) {
                    terminalPublicModelId = modelId;
                }
                YesSteveModel.LOGGER.warn("[SM] Failed to restore selected Cloud model {}", modelId, failure);
                return;
            }
            if (!stillSelected(runtime, worldGeneration, modelId)) {
                inFlight = false;
                return;
            }
            ClientModelManager.importCloudModelInMemory(modelId, CloudAssetImportName.fileName(asset), bytes,
                    () -> request == requestGeneration && stillSelected(runtime, worldGeneration, modelId), error -> {
                if (request != requestGeneration) return;
                inFlight = false;
                if (error == null && stillSelected(runtime, worldGeneration, modelId)) {
                    restoreSelection(runtime, worldGeneration, saved);
                }
            });
        }));
    }

    static String savedFormat(CloudAssetRef ref, List<CloudAssetSummary> recent) {
        return recent.stream().filter(asset -> asset.ref().equals(ref)).map(CloudAssetSummary::format).findFirst().orElse(null);
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

    static Optional<String> preferredInstanceId(String modelId,
            List<CloudInstanceRegistry.CloudInstanceProfile> profiles) {
        if (!CloudAssetIdentity.isRuntimeModelId(modelId)) return Optional.empty();
        for (CloudInstanceRegistry.CloudInstanceProfile profile : profiles) {
            if (findAsset(profile.instanceId(), modelId,
                    CloudModelSelectionStore.recent(profile.instanceId())) != null) {
                return Optional.of(profile.instanceId());
            }
        }
        return Optional.empty();
    }

    static boolean terminalPublicCdnMiss(CloudAssetSummary asset, Throwable failure, boolean cdnEnabled) {
        if (!cdnEnabled || asset == null || !asset.isPublic()) return false;
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current instanceof CloudHttpException http && http.statusCode() == 404;
    }

    private static void restoreSelection(CloudClientRuntime.RuntimeState runtime, long worldGeneration, Pair<String, String> expected) {
        if (!stillSelected(runtime, worldGeneration, expected.getLeft()) || !expected.equals(LocalModelSelectionStore.load())) return;
        // The persisted Cloud choice wins over a provisional local/default display during startup.
        // Successful materialization has already authenticated this exact revision for this runtime.
        com.micaftic.morpher.capability.PlayerCapability.get(Minecraft.getInstance().player).ifPresent(cap -> {
            if (expected.getLeft().equals(cap.getModelId()) && expected.getRight().equals(cap.getCurrentTextureName())
                    && !cap.isForceDisabled() && expected.getLeft().equals(ClientModelManager.MODEL_SELECTION.selectedModelId())) return;
            ClientModelManager.rememberSelectedModel(expected.getLeft(), expected.getRight());
            if (!expected.getLeft().equals(cap.getModelId()) || !expected.getRight().equals(cap.getCurrentTextureName()))
                cap.initModelWithTexture(expected.getLeft(), expected.getRight());
            cap.setForceDisabled(false);
        });
    }

    private static boolean stillSelected(CloudClientRuntime.RuntimeState runtime, long worldGeneration, String modelId) {
        Pair<String, String> saved = LocalModelSelectionStore.load();
        return CloudClientRuntime.state() == runtime
                && CloudClientRuntime.isCurrentWorldGeneration(worldGeneration)
                && Minecraft.getInstance().player != null
                && com.micaftic.morpher.core.network.NetworkAccess.allowsCloud()
                && !com.micaftic.morpher.client.PrivacyMode.isActive()
                && saved != null && modelId.equals(saved.getLeft());
    }
}
