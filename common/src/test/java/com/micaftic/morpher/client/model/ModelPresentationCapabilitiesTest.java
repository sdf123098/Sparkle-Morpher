package com.micaftic.morpher.client.model;

import com.micaftic.morpher.client.animation.condition.ArmorConditions;
import com.micaftic.morpher.client.gui.ModelMetadataPresenter;
import com.micaftic.morpher.client.gui.metadata.ModelDisplayAssets;
import com.micaftic.morpher.geckolib3.core.controller.controllers.ModelActionProviderRegistry;
import com.micaftic.morpher.geckolib3.core.controller.controllers.PlayerActionProvider;
import com.micaftic.morpher.geckolib3.core.builder.Animation;
import com.micaftic.morpher.geckolib3.core.builder.AnimationController;
import com.micaftic.morpher.resource.gltf.GltfLoader;
import com.micaftic.morpher.util.data.OrderedStringMap;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelPresentationCapabilitiesTest {
    @Test
    void legacyBundleRetainsPreviewTextureSelectionAndTrimCapabilitiesAfterRuntimeRelease() {
        ModelResourceBundle resources = new ModelResourceBundle(
                Map.of(), new Object2ReferenceOpenHashMap<>(), new Object2ReferenceOpenHashMap<>(), Map.of());
        PlayerActionProvider previousProvider = ModelActionProviderRegistry.get(ModelActionProfile.VANILLA_HUMANOID);
        ModelActionProviderRegistry.register(ModelActionProfile.VANILLA_HUMANOID,
                (modelBundle, resourceBundle) -> entity -> {});
        PlayerModelBundle bundle;
        try {
            bundle = new PlayerModelBundle(
                    (GeoModel) null,
                    null,
                    new Object2ReferenceOpenHashMap<String, Animation>(),
                    new Object2ReferenceOpenHashMap<String, Animation>(),
                    null,
                    new ArmorConditions(),
                    new Object2ReferenceOpenHashMap<String, AnimationController>(),
                    new OrderedStringMap<>(new String[]{"legacy"}, new AbstractTexture[]{null}),
                    "legacy",
                    null,
                    resources,
                    ModelSourceFormat.BBMODEL,
                    ModelActionProfile.VANILLA_HUMANOID,
                    null,
                    null);
        } finally {
            ModelActionProviderRegistry.register(ModelActionProfile.VANILLA_HUMANOID, previousProvider);
        }
        ModelAssembly assembly = new ModelAssembly(bundle, Map.of(), Map.of(), resources, null,
                new ModelDisplayAssets(null, false, Map.of(), Map.of()), List.of());

        ModelPresentationCapabilities resident = assembly.getPresentationCapabilities();
        assertTrue(resident.runtimeResident());
        assertTrue(resident.textureLabelsAvailable());
        assertTrue(resident.textureSelectionAvailable());
        assertTrue(resident.cardPreviewAvailable());
        assertTrue(resident.detailPreviewAvailable());
        assertTrue(resident.playerActionControlsAvailable());
        assertTrue(resident.gpuTrimAvailable());
        assertTrue(resident.nativeTrimAvailable());
        assertEquals(List.of("legacy"), assembly.getTextureNames());

        assembly.unloadRuntime();
        ModelPresentationCapabilities released = assembly.getPresentationCapabilities();
        assertFalse(released.runtimeResident());
        assertFalse(released.textureSelectionAvailable());
        assertFalse(released.cardPreviewAvailable());
        assertFalse(released.detailPreviewAvailable());
        assertFalse(released.playerActionControlsAvailable());
        assertFalse(released.gpuTrimAvailable());
        assertFalse(released.nativeTrimAvailable());
        assertEquals(List.of("legacy"), assembly.getTextureNames());
    }

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
