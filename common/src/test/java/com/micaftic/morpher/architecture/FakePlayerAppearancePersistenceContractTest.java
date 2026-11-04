package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakePlayerAppearancePersistenceContractTest {
    @Test
    void localModelReloadUsesTheDedicatedModelPipeline() throws IOException {
        String service = RepoSourceLookup.readSource("com/micaftic/morpher/model/LocalModelService.java");
        String manager = RepoSourceLookup.readSource("com/micaftic/morpher/client/ClientModelManager.java");

        assertTrue(service.contains("LocalModelDefinitionCatalog"));
        assertTrue(service.contains("scanDirectoryModels"));
        assertTrue(manager.contains("reloadLocalModels("));
        assertFalse(manager.contains("ServerModelManager"));
    }

    @Test
    void fakePlayerTargetsUseCloudClientState() throws IOException {
        String sync = RepoSourceLookup.readSource("com/micaftic/morpher/cloud/client/CloudEntityModelSync.java");

        assertTrue(sync.contains("refreshFakeTargets"));
        assertTrue(sync.contains("FakePlayerListCache.replace(rows)"));
        assertTrue(sync.contains("applySelection"));
        assertTrue(sync.contains("CloudEntityPresenceClient"));
    }
}
