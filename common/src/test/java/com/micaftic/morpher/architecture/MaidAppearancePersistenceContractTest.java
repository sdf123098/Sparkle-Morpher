package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MaidAppearancePersistenceContractTest {
    @Test
    void maidAppearanceUsesTheCloudClientPathAndKeepsClientCompat() throws IOException {
        String sync = RepoSourceLookup.readSource("com/micaftic/morpher/cloud/client/CloudEntityModelSync.java");
        String compat = RepoSourceLookup.readSource(
                "com/micaftic/morpher/client/compat/touhoulittlemaid/TouhouLittleMaidClientCompat.java");

        assertTrue(sync.contains("cap.applyCloudState(modelId"));
        assertTrue(sync.contains("ClientModelManager.acquireRuntimeModelLease(modelId)"));
        assertTrue(sync.contains("cap.clearCloudState()"));
        assertTrue(compat.contains("handleMaidInteraction"));
        assertTrue(compat.contains("registerMaidAnimStates"));
    }
}
