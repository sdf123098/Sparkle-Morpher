package com.micaftic.morpher.client.model;

import com.micaftic.morpher.client.gui.ModelMetadataPresenter;
import com.micaftic.morpher.resource.gltf.GltfLoader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelPresentationCapabilitiesTest {
    @Test
    void gltfCapabilitiesKeepUnsupportedPresentationFeaturesUnavailableAfterRelease() throws Exception {
        var model = GltfLoader.load(
                "{\"asset\":{\"version\":\"2.0\"}}".getBytes(StandardCharsets.UTF_8), null, "model.gltf");
        ModelAssembly assembly = ModelAssembly.forGltf(model, List.of());

        ModelPresentationCapabilities capabilities = assembly.getPresentationCapabilities();
        assertTrue(capabilities.runtimeResident());
        assertFalse(capabilities.metadataAvailable());
        assertFalse(capabilities.textureLabelsAvailable());
        assertFalse(capabilities.textureSelectionAvailable());
        assertFalse(capabilities.cardPreviewAvailable());
        assertFalse(capabilities.detailPreviewAvailable());
        assertFalse(capabilities.playerActionControlsAvailable());
        assertFalse(capabilities.gpuTrimAvailable());
        assertFalse(capabilities.nativeTrimAvailable());
        assertSame(capabilities, assembly.getPresentationCapabilities());
        assertEquals("Model", ModelMetadataPresenter.getLocalizedModelStringForLocale(
                assembly, "en_us", "metadata.name", "Model"));
        assertTrue(ModelMetadataPresenter.buildModelTooltip(assembly, "en_us", "model.gltf", true).isEmpty());

        assembly.unloadRuntime();
        ModelPresentationCapabilities released = assembly.getPresentationCapabilities();
        assertNotSame(capabilities, released);
        assertFalse(released.runtimeResident());
        assertFalse(released.metadataAvailable());
        assertFalse(released.cardPreviewAvailable());
        assertEquals("Model", ModelMetadataPresenter.getLocalizedModelStringForLocale(
                assembly, "en_us", "metadata.name", "Model"));
    }
}
