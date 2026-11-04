package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SlashBladeModelAttachmentContractTest {
    @Test
    void mainHandBladeUsesModelLocatorsBeforeEntitySpaceMmdFallback() throws IOException {
        // Both classes are loader-divergent: the facade and the bridge each live in a
        // loader tree (fabric/ or neoforge/), never in the shared common/ layer.
        String facadeSource = RepoSourceLookup.readSource(
                "com/micaftic/morpher/core/compat/slashblade/SlashBladeRenderer.java");
        String bridgeSource = RepoSourceLookup.readSource(
                "com/micaftic/morpher/core/compat/slashblade/SlashBladeBridge.java");

        assertTrue(facadeSource.contains("renderMainHandBlade(entity, model"));
        assertTrue(bridgeSource.contains("model.bladeBones()"));
        assertTrue(bridgeSource.contains("model.leftHandBones()"));
        assertTrue(bridgeSource.contains("model.sheathBones()"));
        assertTrue(bridgeSource.contains("RenderUtils.prepMatrixForLocator"));
        assertTrue(bridgeSource.contains("poseStack.scale((float) MODEL_SCALE_BASE"));
    }
}
