package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.core.model.CloudAssetIdentity;
import com.micaftic.morpher.cloud.CloudInstanceConfig;
import com.micaftic.morpher.core.storage.ModelStoragePaths;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudSelectedModelRecoveryTest {
    @TempDir Path root;

    @AfterEach
    void resetStorageRoot() {
        ModelStoragePaths.withRoot(null);
        CloudSelectedModelRecovery.reset();
    }

    @Test
    void selectedRevisionIsRecoveredOnlyFromItsOwnInstance() {
        CloudAssetSummary older = new CloudAssetSummary(new CloudAssetRef("skin", 1, "a".repeat(64)), "skin", "ysm", 8);
        CloudAssetSummary selected = new CloudAssetSummary(new CloudAssetRef("skin", 2, "b".repeat(64)), "skin", "glb", 8);
        String selectedId = new CloudAssetIdentity("official", "catalog", "skin", "2", "b".repeat(64)).runtimeModelId();

        assertEquals(selected, CloudSelectedModelRecovery.findAsset("official", selectedId, List.of(older, selected)));
        assertNull(CloudSelectedModelRecovery.findAsset("other", selectedId, List.of(selected)));
    }

    @Test
    void preferredInstanceFindsPersistedPublicAndPrivateModelsAcrossEveryConfiguredCloud() {
        ModelStoragePaths.withRoot(root);
        var official = new CloudInstanceRegistry.CloudInstanceProfile(
                CloudInstanceConfig.v1("official", URI.create("https://official.example")), "Official");
        var community = new CloudInstanceRegistry.CloudInstanceProfile(
                CloudInstanceConfig.v1("community", URI.create("https://community.example")), "Community");
        var publicAsset = new CloudAssetSummary(new CloudAssetRef("public/skin", 2, "a".repeat(64)),
                "public", "ysm", 8, "PUBLIC");
        var privateAsset = new CloudAssetSummary(new CloudAssetRef("private/skin", 7, "b".repeat(64)),
                "private", "glb", 9, "PRIVATE");
        var store = new CloudModelSelectionStore.Index(root.resolve("cloud_model_selection.json"));
        store.recordApplied("official", publicAsset);
        store.recordApplied("community", privateAsset);

        String publicModel = new CloudAssetIdentity("official", "catalog", publicAsset.ref().assetId(),
                "2", publicAsset.ref().rawSha256()).runtimeModelId();
        String privateModel = new CloudAssetIdentity("community", "catalog", privateAsset.ref().assetId(),
                "7", privateAsset.ref().rawSha256()).runtimeModelId();

        assertEquals(Optional.of("official"), CloudSelectedModelRecovery.preferredInstanceId(
                publicModel, List.of(community, official)));
        assertEquals(Optional.of("community"), CloudSelectedModelRecovery.preferredInstanceId(
                privateModel, List.of(official, community)));
        assertEquals(Optional.empty(), CloudSelectedModelRecovery.preferredInstanceId(
                "local:model", List.of(official, community)));
    }

    @Test
    void loadingInstancesSelectsTheCloudThatOwnsThePersistedModelBeforeAutoLogin() throws Exception {
        ModelStoragePaths.withRoot(root);
        var registryFile = root.resolve("cloud-instances.json");
        var registry = new CloudInstanceRegistry(registryFile);
        var official = new CloudInstanceRegistry.CloudInstanceProfile(
                CloudInstanceConfig.v1("official", URI.create("https://official.example")), "Official");
        var community = new CloudInstanceRegistry.CloudInstanceProfile(
                CloudInstanceConfig.v1("community", URI.create("https://community.example")), "Community");
        registry.addOrReplace(official);
        registry.addOrReplace(community);
        registry.select("official");
        registry.save();

        var privateAsset = new CloudAssetSummary(new CloudAssetRef("private/skin", 7, "b".repeat(64)),
                "private", "glb", 9, "PRIVATE");
        new CloudModelSelectionStore.Index(root.resolve("cloud_model_selection.json"))
                .recordApplied("community", privateAsset);
        String modelId = new CloudAssetIdentity("community", "catalog", privateAsset.ref().assetId(),
                "7", privateAsset.ref().rawSha256()).runtimeModelId();
        var controller = new CloudManagementController(registry, new CloudConnectionController(),
                root.resolve("cache"), "test", () -> Pair.of(modelId, "blue"));
        controller.loadInstances();

        assertEquals("community", registry.selected().orElseThrow().instanceId());
        assertEquals("community", controller.snapshot().profile().instanceId());
    }

    @Test
    void onlyPublicCdnNotFoundIsATerminalRecoveryFailure() {
        var ref = new CloudAssetRef("skin", 1, "a".repeat(64));
        var publicAsset = new CloudAssetSummary(ref, "skin", "ysm", 8, "PUBLIC");
        var privateAsset = new CloudAssetSummary(ref, "skin", "ysm", 8, "PRIVATE");
        var notFound = new CloudHttpException(404,
                com.micaftic.morpher.core.api.network.state.CloudErrorCode.ASSET_NOT_FOUND, "missing");
        var unavailable = new CloudHttpException(503,
                com.micaftic.morpher.core.api.network.state.CloudErrorCode.INTERNAL, "unavailable");

        assertTrue(CloudSelectedModelRecovery.terminalPublicCdnMiss(publicAsset, notFound, true));
        assertFalse(CloudSelectedModelRecovery.terminalPublicCdnMiss(privateAsset, notFound, true));
        assertFalse(CloudSelectedModelRecovery.terminalPublicCdnMiss(publicAsset, notFound, false));
        assertFalse(CloudSelectedModelRecovery.terminalPublicCdnMiss(publicAsset, unavailable, true));
    }
}
