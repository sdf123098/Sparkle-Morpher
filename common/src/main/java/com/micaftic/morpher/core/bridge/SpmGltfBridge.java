package com.micaftic.morpher.core.bridge;

import com.micaftic.morpher.core.acceleration.AccelerationCapability;
import dev.spm.core.api.BonePose;
import dev.spm.core.api.CancellationToken;
import dev.spm.core.api.DecodePolicy;
import dev.spm.core.api.FormatHint;
import dev.spm.core.api.ImportRequest;
import dev.spm.core.api.ImportResult;
import dev.spm.core.api.ModelAsset;
import dev.spm.core.api.ModelSource;
import dev.spm.core.api.ResourceBudget;
import dev.spm.core.api.ResourceKey;
import dev.spm.core.api.ResourceResolver;
import dev.spm.core.api.SkinState;
import dev.spm.core.parser.SpmModelParser;
import dev.spm.core.runtime.CpuFrame;
import dev.spm.core.runtime.ModelFrameRenderer;
import dev.spm.core.scene.GltfAnimationLibrary;
import dev.spm.core.scene.ScriptBudget;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Host bridge to {@code spm-core 1.0.0}'s full glTF 2.0 importer, {@link GltfAnimationLibrary}
 * ({@code STEP}, {@code LINEAR}, {@code CUBICSPLINE} + morph weights), and CPU skinning/morph
 * evaluator ({@link ModelFrameRenderer}).
 */
public final class SpmGltfBridge {
    private static final SpmModelParser PARSER = new SpmModelParser();

    private SpmGltfBridge() {
    }

    public static final class ImportedGltf implements AutoCloseable {
        private final ImportResult importResult;
        private final ModelAsset primaryAsset;
        private final GltfAnimationLibrary animations;

        ImportedGltf(ImportResult importResult, ModelAsset primaryAsset, GltfAnimationLibrary animations) {
            this.importResult = importResult;
            this.primaryAsset = primaryAsset;
            this.animations = animations;
        }

        public ImportResult importResult() {
            return importResult;
        }

        public ModelAsset primaryAsset() {
            return primaryAsset;
        }

        public GltfAnimationLibrary animations() {
            return animations;
        }

        public List<String> clipNames() {
            return animations.clipNames();
        }

        public CpuFrame evaluateFrame(String clipName, double timeSeconds, boolean looping,
                                      ResourceBudget budget, CancellationToken token) {
            ScriptBudget scriptBudget = new ScriptBudget(250_000L, 64, token);
            BonePose pose;
            SkinState skinState;
            if (clipName != null && animations.hasClips()) {
                GltfAnimationLibrary.EvaluatedFrame evaluated = animations.evaluate(
                        primaryAsset, clipName, timeSeconds, looping, scriptBudget);
                pose = evaluated.pose();
                skinState = evaluated.skinState();
            } else {
                pose = primaryAsset.document().bindPose();
                skinState = primaryAsset.defaultSkinState();
            }
            if (primaryAsset.skinningRig().isPresent()) {
                return ModelFrameRenderer.render(
                        primaryAsset, pose, primaryAsset.skinningRig().get(), skinState,
                        AccelerationCapability.computeProvider(), budget, token);
            }
            return ModelFrameRenderer.render(
                    primaryAsset, pose, AccelerationCapability.computeProvider(), budget, token);
        }

        @Override
        public void close() {
            importResult.close();
        }
    }

    public static ImportedGltf parse(byte[] data, String fileName) throws IOException {
        return parse(data, fileName, null);
    }

    public static ImportedGltf parse(byte[] data, String fileName, ResourceResolver sidecarResolver) throws IOException {
        Objects.requireNonNull(data);
        String entry = (fileName == null || fileName.isBlank()) ? "model.glb" : fileName;
        FormatHint hint = entry.toLowerCase(java.util.Locale.ROOT).endsWith(".gltf")
                ? FormatHint.GLTF : FormatHint.GLB;
        ResourceResolver resolver = key -> {
            if (key.path().equals(entry)) {
                return new ByteArrayInputStream(data);
            }
            if (sidecarResolver != null) {
                return sidecarResolver.open(key);
            }
            throw new IOException("Sidecar glTF resource not found: " + key.path());
        };
        ImportRequest request = new ImportRequest(
                new ModelSource("gltf:" + entry, new ResourceKey(entry), resolver),
                hint,
                DecodePolicy.localModel(),
                new ResourceBudget(512L << 20),
                new CancellationToken());
        ImportResult result = PARSER.importModel(request);
        if (result.models().isEmpty()) {
            result.close();
            throw new IOException("No glTF model produced by spm-core: " + entry);
        }
        ModelAsset primary = result.models().get(0).asset();
        GltfAnimationLibrary library = GltfAnimationLibrary.compile(primary.document());
        return new ImportedGltf(result, primary, library);
    }
}
