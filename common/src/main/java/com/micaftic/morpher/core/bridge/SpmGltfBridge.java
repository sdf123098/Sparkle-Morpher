package com.micaftic.morpher.core.bridge;

import com.micaftic.morpher.core.acceleration.AccelerationCapability;
import dev.spm.core.api.BoneMatrices;
import dev.spm.core.api.BonePose;
import dev.spm.core.api.CancellationToken;
import dev.spm.core.api.DecodePolicy;
import dev.spm.core.api.FormatHint;
import dev.spm.core.api.ImportRequest;
import dev.spm.core.api.ImportResult;
import dev.spm.core.api.ModelAsset;
import dev.spm.core.api.ModelDocument;
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
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        private final Map<Integer, List<String>> nodeMeshNames;
        private final boolean hasMorphTargets;

        ImportedGltf(ImportResult importResult, ModelAsset primaryAsset, GltfAnimationLibrary animations) {
            this.importResult = importResult;
            this.primaryAsset = primaryAsset;
            this.animations = animations;
            this.nodeMeshNames = indexNodeMeshNames(primaryAsset.document());
            this.hasMorphTargets = !primaryAsset.defaultSkinState().isEmpty();
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

        public boolean hasMorphTargets() {
            return hasMorphTargets;
        }

        public @Nullable String resolveNodeMeshName(int nodeIndex, int primitiveIndex) {
            List<String> names = nodeMeshNames.get(nodeIndex);
            if (names == null || primitiveIndex < 0 || primitiveIndex >= names.size()) {
                return null;
            }
            return names.get(primitiveIndex);
        }

        public GltfAnimationLibrary.EvaluatedFrame evaluateAnimation(@Nullable String clipName,
                                                                    int animationIndex,
                                                                    double timeSeconds,
                                                                    boolean looping,
                                                                    CancellationToken token) {
            String resolvedName = clipName;
            if ((resolvedName == null || !animations.clipNames().contains(resolvedName))
                    && animationIndex >= 0 && animationIndex < animations.clipNames().size()) {
                resolvedName = animations.clipNames().get(animationIndex);
            }
            if (resolvedName != null && animations.hasClips()) {
                ScriptBudget scriptBudget = new ScriptBudget(250_000L, 64, token);
                return animations.evaluate(primaryAsset, resolvedName, timeSeconds, looping, scriptBudget);
            }
            return new GltfAnimationLibrary.EvaluatedFrame(
                    primaryAsset.document().bindPose(),
                    primaryAsset.defaultSkinState());
        }

        public BoneMatrices evaluateBones(BonePose pose) {
            return AccelerationCapability.computeProvider()
                    .evaluateBones(primaryAsset.document().skeleton(), pose);
        }

        public CpuFrame renderPoseFrame(BonePose pose, SkinState skinState,
                                          ResourceBudget budget, CancellationToken token) {
            if (primaryAsset.skinningRig().isPresent()) {
                return ModelFrameRenderer.render(
                        primaryAsset, pose, primaryAsset.skinningRig().get(), skinState,
                        AccelerationCapability.computeProvider(), budget, token);
            }
            return ModelFrameRenderer.render(
                    primaryAsset, pose, AccelerationCapability.computeProvider(), budget, token);
        }

        public CpuFrame evaluateFrame(String clipName, double timeSeconds, boolean looping,
                                      ResourceBudget budget, CancellationToken token) {
            GltfAnimationLibrary.EvaluatedFrame evaluated = evaluateAnimation(
                    clipName, -1, timeSeconds, looping, token);
            return renderPoseFrame(evaluated.pose(), evaluated.skinState(), budget, token);
        }

        private static Map<Integer, List<String>> indexNodeMeshNames(ModelDocument document) {
            Map<Integer, List<String>> map = new LinkedHashMap<>();
            ModelDocument.JsonValue raw = document.metadata().values().get("gltfNodeMeshes");
            if (!(raw instanceof ModelDocument.JsonArray arr)) {
                return Map.of();
            }
            for (ModelDocument.JsonValue elem : arr.values()) {
                if (!(elem instanceof ModelDocument.JsonObject obj)) {
                    continue;
                }
                ModelDocument.JsonValue meshVal = obj.values().get("mesh");
                ModelDocument.JsonValue nodeVal = obj.values().get("nodeIndex");
                if (meshVal instanceof ModelDocument.JsonString meshStr
                        && nodeVal instanceof ModelDocument.JsonNumber nodeNum) {
                    int nodeIdx = (int) nodeNum.value();
                    map.computeIfAbsent(nodeIdx, unused -> new ArrayList<>()).add(meshStr.value());
                }
            }
            return map;
        }

        @Override
        public void close() {
            importResult.close();
        }
    }

    public static ImportedGltf parse(byte[] data, String fileName) throws IOException {
        return parse(data, fileName, (ResourceResolver) null);
    }

    public static ImportedGltf parse(byte[] data, String fileName, @Nullable Path authorizedRoot) throws IOException {
        ResourceResolver rootResolver = authorizedRoot == null ? null : pathKey -> {
            Path normalizedRoot = authorizedRoot.toAbsolutePath().normalize().toRealPath();
            Path candidate = normalizedRoot.resolve(pathKey.path()).normalize();
            if (!candidate.startsWith(normalizedRoot)) {
                throw new IOException("glTF resource escapes authorized root: " + pathKey.path());
            }
            Path real = candidate.toRealPath();
            if (!real.startsWith(normalizedRoot) || !Files.isRegularFile(real)) {
                throw new IOException("glTF resource escapes authorized root or is missing: " + pathKey.path());
            }
            return Files.newInputStream(real);
        };
        return parse(data, fileName, rootResolver);
    }

    public static @Nullable ImportedGltf tryParse(byte[] data, String fileName,
                                                  @Nullable Path authorizedRoot,
                                                  boolean hasCustomExtensions) {
        if (hasCustomExtensions || data == null || data.length == 0) {
            return null;
        }
        try {
            return parse(data, fileName, authorizedRoot);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static ImportedGltf parse(byte[] data, String fileName, @Nullable ResourceResolver sidecarResolver) throws IOException {
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
