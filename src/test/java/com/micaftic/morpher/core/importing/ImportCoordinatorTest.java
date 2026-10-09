package com.micaftic.morpher.core.importing;

import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.model.ModelResourceBundle;
import com.micaftic.morpher.client.model.PlayerModelBundle;
import com.micaftic.morpher.client.model.ModelActionProfile;
import com.micaftic.morpher.client.model.ModelSourceFormat;
import com.micaftic.morpher.client.gui.metadata.ModelDisplayAssets;
import com.micaftic.morpher.client.animation.condition.ArmorConditions;
import com.micaftic.morpher.geckolib3.core.controller.controllers.ModelActionProviderRegistry;
import com.micaftic.morpher.geckolib3.core.controller.controllers.PlayerActionProvider;
import com.micaftic.morpher.geckolib3.core.builder.Animation;
import com.micaftic.morpher.geckolib3.core.builder.AnimationController;
import com.micaftic.morpher.util.data.OrderedStringMap;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.minecraft.client.renderer.texture.AbstractTexture;
import com.micaftic.morpher.resource.gltf.GltfModel;
import com.micaftic.morpher.core.model.selection.ModelSelectionState;
import com.micaftic.morpher.core.storage.LocalModelImportStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportCoordinatorTest {
    @TempDir
    Path tempDir;

    @Test
    void gltfBytesBecomeTypedPayloadWithNoInventedResourceRoot() throws Exception {
        byte[] source = "{\"asset\":{\"version\":\"2.0\"}}".getBytes(StandardCharsets.UTF_8);
        AtomicBoolean legacyParserCalled = new AtomicBoolean();

        ParsedImport parsed = ImportCoordinator.parsePickedBytes("picked.gltf", source, () -> {
            legacyParserCalled.set(true);
            return new RawYsmModel();
        });

        ParsedImport.GltfPayload payload = assertInstanceOf(ParsedImport.GltfPayload.class, parsed.payload());
        assertEquals(ImportSource.Kind.PICKED_BYTES, parsed.source().kind());
        assertEquals(ImportSource.Format.GLTF, parsed.source().format());
        assertTrue(payload.result().dependencies().entries().isEmpty());
        assertFalse(legacyParserCalled.get());
    }

    @Test
    void legacyBytesUseLegacyParserAndRemainTypedAsRawYsm() throws Exception {
        RawYsmModel expected = new RawYsmModel();
        AtomicBoolean legacyParserCalled = new AtomicBoolean();

        ParsedImport parsed = ImportCoordinator.parsePickedBytes("picked.bbmodel", new byte[]{1}, () -> {
            legacyParserCalled.set(true);
            return expected;
        });

        ParsedImport.LegacyRawPayload payload = assertInstanceOf(ParsedImport.LegacyRawPayload.class, parsed.payload());
        assertEquals(ImportSource.Format.BBMODEL, parsed.source().format());
        assertTrue(legacyParserCalled.get());
        assertEquals(expected, payload.model());
    }

    @Test
    void unsupportedFormatFailsBeforeCallingFormatParser() {
        AtomicBoolean legacyParserCalled = new AtomicBoolean();
        assertThrows(IllegalArgumentException.class,
                () -> ImportCoordinator.parsePickedBytes("picked.obj", new byte[]{1}, () -> {
                    legacyParserCalled.set(true);
                    return new RawYsmModel();
                }));
        assertFalse(legacyParserCalled.get());
    }

    @Test
    void localGltfPathProducesEquivalentTypedPayloadWithExplicitRoot() throws Exception {
        Path source = Files.createTempFile("picked-local-", ".gltf");
        Files.writeString(source, "{\"asset\":{\"version\":\"2.0\"}}", StandardCharsets.UTF_8);

        ParsedImport parsed = ImportCoordinator.parseLocalGltf(source);
        ParsedImport.GltfPayload payload = assertInstanceOf(ParsedImport.GltfPayload.class, parsed.payload());

        assertEquals(ImportSource.Kind.LOCAL_PATH, parsed.source().kind());
        assertEquals(source.getParent().toAbsolutePath().normalize(), parsed.source().authorizedRoot());
        assertEquals(com.micaftic.morpher.resource.gltf.GltfLoader.sourceFingerprint(source),
                payload.result().sourceFingerprint());
    }

    @Test
    void localGltfParserRejectsOtherFormats() throws Exception {
        Path source = Files.createTempFile("picked-local-", ".ysm");
        assertThrows(java.io.IOException.class, () -> ImportCoordinator.parseLocalGltf(source));
    }

    @Test
    void pickedAndLocalGltfUseTheSameBackendAdapterAndIdentity() throws Exception {
        byte[] bytes = "{\"asset\":{\"version\":\"2.0\"}}".getBytes(StandardCharsets.UTF_8);
        Path source = Files.write(tempDir.resolve("same.gltf"), bytes);
        ParsedImport picked = ImportCoordinator.parsePickedBytes("same.gltf", bytes, RawYsmModel::new);
        ParsedImport local = ImportCoordinator.parseLocalGltf(source);
        List<String> adapters = new ArrayList<>();

        String pickedCandidate = ImportCoordinator.buildCandidate(picked,
                raw -> { adapters.add("legacy"); return "legacy"; },
                result -> { adapters.add("gltf"); return Long.toUnsignedString(result.sourceFingerprint()); });
        String localCandidate = ImportCoordinator.buildCandidate(local,
                raw -> { adapters.add("legacy"); return "legacy"; },
                result -> { adapters.add("gltf"); return Long.toUnsignedString(result.sourceFingerprint()); });

        assertEquals(List.of("gltf", "gltf"), adapters);
        assertEquals(pickedCandidate, localCandidate);
    }

    @Test
    void legacyAndGltfPayloadsReachOnlyTheirOwnBackendAdapter() throws Exception {
        ParsedImport legacy = ImportCoordinator.parsePickedBytes("same.bbmodel", new byte[]{1}, RawYsmModel::new);
        ParsedImport gltf = ImportCoordinator.parsePickedBytes("same.gltf",
                "{\"asset\":{\"version\":\"2.0\"}}".getBytes(StandardCharsets.UTF_8), RawYsmModel::new);
        List<String> adapters = new ArrayList<>();

        String legacyCandidate = ImportCoordinator.buildCandidate(legacy,
                raw -> { adapters.add("legacy"); return "legacy-candidate"; },
                result -> { adapters.add("gltf"); return "unexpected-gltf-candidate"; });
        String gltfCandidate = ImportCoordinator.buildCandidate(gltf,
                raw -> { adapters.add("legacy"); return "unexpected-legacy-candidate"; },
                result -> { adapters.add("gltf"); return "gltf-candidate"; });

        assertEquals("legacy-candidate", legacyCandidate);
        assertEquals("gltf-candidate", gltfCandidate);
        assertEquals(List.of("legacy", "gltf"), adapters);
    }

    @Test
    void commitBuiltCandidatePublishesBeforeTransferringOwnership() {
        Object candidate = new Object();
        List<String> events = new ArrayList<>();

        var outcome = ImportCoordinator.commitBuiltCandidate(candidate, () -> true,
                () -> { events.add("commit"); return "persisted"; },
                (published, source) -> { assertEquals(candidate, published); events.add("publish:" + source); },
                ignored -> events.add("release"));

        assertEquals(com.micaftic.morpher.core.storage.ImportCommitFlow.State.PUBLISHED, outcome.state());
        assertEquals(List.of("commit", "publish:persisted"), events);
    }

    @Test
    void commitBuiltCandidateReleasesOnSupersedeCommitFailureAndPublishFailure() {
        Object candidate = new Object();
        List<String> events = new ArrayList<>();

        var superseded = ImportCoordinator.commitBuiltCandidate(candidate, () -> false,
                () -> { events.add("commit"); return "unused"; },
                (ignored, source) -> events.add("publish"), ignored -> events.add("release-superseded"));
        var commitFailed = ImportCoordinator.commitBuiltCandidate(candidate, () -> true,
                () -> { throw new java.io.IOException("disk full"); },
                (ignored, source) -> events.add("publish-commit-failure"), ignored -> events.add("release-commit-failure"));
        var publishFailed = ImportCoordinator.commitBuiltCandidate(candidate, () -> true,
                () -> "committed",
                (ignored, source) -> { throw new IllegalStateException("runtime rejected candidate"); },
                ignored -> events.add("release-publish-failure"));

        assertEquals(com.micaftic.morpher.core.storage.ImportCommitFlow.State.SUPERSEDED_BEFORE_COMMIT, superseded.state());
        assertEquals(com.micaftic.morpher.core.storage.ImportCommitFlow.State.FAILED_BEFORE_COMMIT, commitFailed.state());
        assertEquals(com.micaftic.morpher.core.storage.ImportCommitFlow.State.SOURCE_COMMITTED_PENDING_PUBLICATION,
                publishFailed.state());
        assertEquals("committed", publishFailed.committedSource());
        assertEquals(List.of("release-superseded", "release-commit-failure", "release-publish-failure"), events);
    }

    @Test
    void legacyAndGltfCandidatesSharePreparedCommitAndPublicationFlow() throws Exception {
        Path customRoot = tempDir.resolve("shared-flow");
        LocalModelImportStore store = new LocalModelImportStore(customRoot);
        Path oldSource = store.persist("avatar", "avatar.ysm", "old-source".getBytes(StandardCharsets.UTF_8));
        List<String> events = new ArrayList<>();

        byte[] legacyBytes = {1};
        try (LocalModelImportStore.PreparedImport prepared = store.prepare("avatar", "avatar.bbmodel", legacyBytes)) {
            ParsedImport parsed = ImportCoordinator.parsePickedBytes("avatar.bbmodel", legacyBytes, RawYsmModel::new);
            String candidate = ImportCoordinator.buildCandidate(parsed,
                    raw -> { events.add("legacy-build"); return "legacy-candidate"; },
                    result -> { events.add("unexpected-gltf-build"); return "unexpected"; });
            var outcome = ImportCoordinator.commitBuiltCandidate(candidate, () -> true,
                    () -> { events.add("legacy-commit"); return prepared.commit(); },
                    (built, committed) -> events.add("legacy-publish:" + committed.persistedPath().getFileName()),
                    ignored -> events.add("legacy-release"));
            assertEquals(com.micaftic.morpher.core.storage.ImportCommitFlow.State.PUBLISHED, outcome.state());
        }

        byte[] gltfBytes = "{\"asset\":{\"version\":\"2.0\"}}".getBytes(StandardCharsets.UTF_8);
        try (LocalModelImportStore.PreparedImport prepared = store.prepare("avatar", "avatar.gltf", gltfBytes)) {
            ParsedImport parsed = ImportCoordinator.parsePickedBytes("avatar.gltf", gltfBytes, RawYsmModel::new);
            String candidate = ImportCoordinator.buildCandidate(parsed,
                    raw -> { events.add("unexpected-legacy-build"); return "unexpected"; },
                    result -> { events.add("gltf-build"); return "gltf-candidate"; });
            var outcome = ImportCoordinator.commitBuiltCandidate(candidate, () -> true,
                    () -> { events.add("gltf-commit"); return prepared.commit(); },
                    (built, committed) -> events.add("gltf-publish:" + committed.persistedPath().getFileName()),
                    ignored -> events.add("gltf-release"));
            assertEquals(com.micaftic.morpher.core.storage.ImportCommitFlow.State.PUBLISHED, outcome.state());
        }

        assertEquals(List.of("legacy-build", "legacy-commit", "legacy-publish:avatar.bbmodel",
                "gltf-build", "gltf-commit", "gltf-publish:avatar.gltf"), events);
        assertFalse(Files.exists(oldSource));
        assertFalse(Files.exists(customRoot.resolve("avatar.bbmodel")));
        assertTrue(Files.exists(customRoot.resolve("avatar.gltf")));
    }

    @Test
    void completePreparedTransactionPreservesExistingSourceAssemblyAndSelectionOnParseOrBuildFailure() throws Exception {
        Path customRoot = tempDir.resolve("preserve-on-failure");
        LocalModelImportStore store = new LocalModelImportStore(customRoot);
        Path oldSource = store.persist("avatar", "avatar.ysm", "old-source".getBytes(StandardCharsets.UTF_8));
        ModelAssembly oldAssembly = legacyAssembly();
        Map<String, ModelAssembly> runtime = new HashMap<>();
        runtime.put("avatar", oldAssembly);
        ModelSelectionState selection = new ModelSelectionState();
        selection.remember("avatar", "default", true, false);
        AtomicBoolean publishCalled = new AtomicBoolean();
        AtomicBoolean releaseCalled = new AtomicBoolean();

        byte[] legacyBytes = {1};
        try (LocalModelImportStore.PreparedImport prepared = store.prepare("avatar", "avatar.bbmodel", legacyBytes)) {
            var parseFailed = ImportCoordinator.importPrepared(prepared,
                    () -> { throw new java.io.IOException("invalid legacy source"); },
                    raw -> new Object(), result -> new Object(),
                    candidate -> ImportCoordinator.commitBuiltCandidate(candidate, () -> true,
                            prepared::commit, (built, committed) -> publishCalled.set(true),
                            released -> releaseCalled.set(true)));
            assertEquals(com.micaftic.morpher.core.storage.ImportCommitFlow.State.FAILED_BEFORE_COMMIT,
                    parseFailed.state());
        }

        byte[] gltfBytes = "{\"asset\":{\"version\":\"2.0\"}}".getBytes(StandardCharsets.UTF_8);
        try (LocalModelImportStore.PreparedImport prepared = store.prepare("avatar", "avatar.gltf", gltfBytes)) {
            var buildFailed = ImportCoordinator.importPrepared(prepared,
                    () -> ImportCoordinator.parsePickedBytes("avatar.gltf", gltfBytes, RawYsmModel::new),
                    raw -> new Object(), result -> { throw new IllegalStateException("backend assembly failed"); },
                    candidate -> ImportCoordinator.commitBuiltCandidate(candidate, () -> true,
                            prepared::commit, (built, committed) -> publishCalled.set(true),
                            released -> releaseCalled.set(true)));
            assertEquals(com.micaftic.morpher.core.storage.ImportCommitFlow.State.FAILED_BEFORE_COMMIT,
                    buildFailed.state());
        }

        try (LocalModelImportStore.PreparedImport prepared = store.prepare("avatar", "avatar.zip", new byte[]{2})) {
            var commitFailed = ImportCoordinator.importPrepared(prepared,
                    () -> ImportCoordinator.parsePickedBytes("avatar.zip", new byte[]{2}, RawYsmModel::new),
                    raw -> new Object(), result -> new Object(),
                    candidate -> ImportCoordinator.commitBuiltCandidate(candidate, () -> true,
                            () -> { throw new java.io.IOException("disk full"); },
                            (built, committed) -> publishCalled.set(true),
                            released -> releaseCalled.set(true)));
            assertEquals(com.micaftic.morpher.core.storage.ImportCommitFlow.State.FAILED_BEFORE_COMMIT,
                    commitFailed.state());
        }

        assertEquals("old-source", Files.readString(oldSource));
        assertFalse(Files.exists(customRoot.resolve("avatar.bbmodel")));
        assertFalse(Files.exists(customRoot.resolve("avatar.gltf")));
        assertSame(oldAssembly, runtime.get("avatar"));
        assertTrue(oldAssembly.isRuntimeResident());
        assertEquals("avatar", selection.selectedModelId());
        assertEquals("default", selection.selectedTextureId());
        assertFalse(publishCalled.get());
        assertTrue(releaseCalled.get());
    }

    @Test
    void completePreparedTransactionDispatchesAndPublishesLegacyThenGltfCandidates() throws Exception {
        Path customRoot = tempDir.resolve("complete-transaction");
        LocalModelImportStore store = new LocalModelImportStore(customRoot);
        Path oldSource = store.persist("avatar", "avatar.ysm", "old-source".getBytes(StandardCharsets.UTF_8));
        Object oldAssembly = new Object();
        Map<String, Object> runtime = new HashMap<>();
        runtime.put("avatar", oldAssembly);
        ModelSelectionState selection = new ModelSelectionState();
        selection.remember("avatar", "texture:legacy", true, false);
        List<String> events = new ArrayList<>();
        Object legacyCandidate = new Object();
        byte[] legacyBytes = {1};

        try (LocalModelImportStore.PreparedImport prepared = store.prepare("avatar", "avatar.bbmodel", legacyBytes)) {
            var outcome = ImportCoordinator.importPrepared(prepared,
                    () -> ImportCoordinator.parsePickedBytes("avatar.bbmodel", legacyBytes, RawYsmModel::new),
                    raw -> { events.add("legacy-build"); return legacyCandidate; },
                    result -> { events.add("unexpected-gltf-build"); return new Object(); },
                    candidate -> ImportCoordinator.commitBuiltCandidate(candidate, () -> true,
                            prepared::commit, (built, committed) -> {
                                events.add("legacy-publish");
                                runtime.put("avatar", built);
                            }, released -> events.add("legacy-release")));
            assertEquals(com.micaftic.morpher.core.storage.ImportCommitFlow.State.PUBLISHED, outcome.state());
        }

        assertFalse(Files.exists(oldSource));
        assertEquals(legacyCandidate, runtime.get("avatar"));
        assertEquals("avatar", selection.selectedModelId());
        assertEquals("texture:legacy", selection.selectedTextureId());

        Object gltfCandidate = new Object();
        byte[] gltfBytes = "{\"asset\":{\"version\":\"2.0\"}}".getBytes(StandardCharsets.UTF_8);
        try (LocalModelImportStore.PreparedImport prepared = store.prepare("avatar", "avatar.gltf", gltfBytes)) {
            var outcome = ImportCoordinator.importPrepared(prepared,
                    () -> ImportCoordinator.parsePickedBytes("avatar.gltf", gltfBytes, RawYsmModel::new),
                    raw -> { events.add("unexpected-legacy-build"); return new Object(); },
                    result -> { events.add("gltf-build"); return gltfCandidate; },
                    candidate -> ImportCoordinator.commitBuiltCandidate(candidate, () -> true,
                            prepared::commit, (built, committed) -> {
                                events.add("gltf-publish");
                                runtime.put("avatar", built);
                            }, released -> events.add("gltf-release")));
            assertEquals(com.micaftic.morpher.core.storage.ImportCommitFlow.State.PUBLISHED, outcome.state());
        }

        assertEquals(List.of("legacy-build", "legacy-publish", "gltf-build", "gltf-publish"), events);
        assertEquals(gltfCandidate, runtime.get("avatar"));
        assertEquals("avatar", selection.selectedModelId());
        assertEquals("texture:legacy", selection.selectedTextureId());
        assertTrue(Files.exists(customRoot.resolve("avatar.gltf")));
        assertFalse(Files.exists(customRoot.resolve("avatar.bbmodel")));
    }

    @Test
    void publicationFailureKeepsOldRuntimeSelectionAndReturnsCommittedSourceForRecovery() throws Exception {
        Path customRoot = tempDir.resolve("publication-recovery");
        LocalModelImportStore store = new LocalModelImportStore(customRoot);
        Path oldSource = store.persist("avatar", "avatar.ysm", "old-source".getBytes(StandardCharsets.UTF_8));
        Object oldAssembly = new Object();
        Map<String, Object> runtime = new HashMap<>();
        runtime.put("avatar", oldAssembly);
        ModelSelectionState selection = new ModelSelectionState();
        selection.remember("avatar", "texture:old", true, false);
        byte[] bytes = {1};

        try (LocalModelImportStore.PreparedImport prepared = store.prepare("avatar", "avatar.bbmodel", bytes)) {
            var outcome = ImportCoordinator.importPrepared(prepared,
                    () -> ImportCoordinator.parsePickedBytes("avatar.bbmodel", bytes, RawYsmModel::new),
                    raw -> new Object(), result -> new Object(),
                    candidate -> ImportCoordinator.commitBuiltCandidate(candidate, () -> true,
                            prepared::commit,
                            (built, committed) -> { throw new IllegalStateException("runtime publication failed"); },
                            released -> {}));

            assertEquals(com.micaftic.morpher.core.storage.ImportCommitFlow.State.SOURCE_COMMITTED_PENDING_PUBLICATION,
                    outcome.state());
            assertEquals(customRoot.resolve("avatar.bbmodel"), outcome.committedSource().persistedPath());
        }

        assertFalse(Files.exists(oldSource));
        assertTrue(Files.exists(customRoot.resolve("avatar.bbmodel")));
        assertEquals(oldAssembly, runtime.get("avatar"));
        assertEquals("avatar", selection.selectedModelId());
        assertEquals("texture:old", selection.selectedTextureId());
    }

    @Test
    void preparedImportBuildsAndPublishesRealGltfAssemblyWithoutChangingSelection() throws Exception {
        Path customRoot = tempDir.resolve("real-assembly");
        LocalModelImportStore store = new LocalModelImportStore(customRoot);
        store.persist("avatar", "avatar.ysm", "old-source".getBytes(StandardCharsets.UTF_8));
        ModelAssembly oldAssembly = ModelAssembly.forGltf(
                ImportCoordinator.parsePickedBytes("old.gltf",
                        "{\"asset\":{\"version\":\"2.0\"}}".getBytes(StandardCharsets.UTF_8), RawYsmModel::new)
                        .payload() instanceof ParsedImport.GltfPayload oldPayload ? oldPayload.result().model() : null,
                List.of());
        Map<String, ModelAssembly> runtime = new HashMap<>();
        runtime.put("avatar", oldAssembly);
        ModelSelectionState selection = new ModelSelectionState();
        selection.remember("avatar", "texture:old", true, false);
        byte[] bytes = "{\"asset\":{\"version\":\"2.0\"}}".getBytes(StandardCharsets.UTF_8);

        try (LocalModelImportStore.PreparedImport prepared = store.prepare("avatar", "avatar.gltf", bytes)) {
            var outcome = ImportCoordinator.importPrepared(prepared,
                    () -> ImportCoordinator.parsePickedBytes("avatar.gltf", bytes, RawYsmModel::new),
                    raw -> { throw new AssertionError("glTF import must not use the legacy assembler"); },
                    result -> ModelAssembly.forGltf(result.model(), List.of()),
                    candidate -> ImportCoordinator.commitBuiltCandidate(candidate, () -> true,
                            prepared::commit,
                            (built, committed) -> runtime.put("avatar", built),
                            ModelAssembly::unloadRuntime));

            assertEquals(com.micaftic.morpher.core.storage.ImportCommitFlow.State.PUBLISHED, outcome.state());
        }

        ModelAssembly published = runtime.get("avatar");
        assertTrue(published.isGltf());
        assertTrue(published.isRuntimeResident());
        assertEquals("avatar", selection.selectedModelId());
        assertEquals("texture:old", selection.selectedTextureId());
        assertTrue(Files.exists(customRoot.resolve("avatar.gltf")));
    }

    @Test
    void preparedTransactionPublishesRealLegacyAndGltfAssembliesAndPreservesSelection() throws Exception {
        Path customRoot = tempDir.resolve("both-real-backends");
        LocalModelImportStore store = new LocalModelImportStore(customRoot);
        Path oldSource = store.persist("avatar", "avatar.ysm", "old-source".getBytes(StandardCharsets.UTF_8));
        ModelAssembly original = legacyAssembly();
        AtomicReference<ModelAssembly> runtime = new AtomicReference<>(original);
        ModelSelectionState selection = new ModelSelectionState();
        selection.remember("avatar", "legacy", true, false);
        List<String> backendCalls = new ArrayList<>();

        byte[] legacyBytes = {1};
        try (LocalModelImportStore.PreparedImport prepared = store.prepare("avatar", "avatar.bbmodel", legacyBytes)) {
            ParsedImport parsed = ImportCoordinator.parsePickedBytes("avatar.bbmodel", legacyBytes, RawYsmModel::new);
            var outcome = ImportCoordinator.importPrepared(prepared, () -> parsed,
                    raw -> { backendCalls.add("legacy"); return legacyAssembly(); },
                    result -> { backendCalls.add("unexpected-gltf"); throw new AssertionError("legacy payload reached glTF adapter"); },
                    candidate -> ImportCoordinator.commitBuiltCandidate(candidate, () -> true,
                            prepared::commit, (built, committed) -> runtime.set(built), ModelAssembly::unloadRuntime));

            assertEquals(com.micaftic.morpher.core.storage.ImportCommitFlow.State.PUBLISHED, outcome.state());
        }
        ModelAssembly legacy = runtime.get();
        assertFalse(legacy.isGltf());
        assertTrue(legacy.isRuntimeResident());
        assertEquals("avatar", selection.selectedModelId());
        assertEquals("legacy", selection.selectedTextureId());
        assertTrue(Files.exists(customRoot.resolve("avatar.bbmodel")));
        assertFalse(Files.exists(oldSource));

        byte[] gltfBytes = "{\"asset\":{\"version\":\"2.0\"}}".getBytes(StandardCharsets.UTF_8);
        try (LocalModelImportStore.PreparedImport prepared = store.prepare("avatar", "avatar.gltf", gltfBytes)) {
            ParsedImport parsed = ImportCoordinator.parsePickedBytes("avatar.gltf", gltfBytes, RawYsmModel::new);
            var outcome = ImportCoordinator.importPrepared(prepared, () -> parsed,
                    raw -> { backendCalls.add("unexpected-legacy"); throw new AssertionError("glTF payload reached legacy adapter"); },
                    result -> { backendCalls.add("gltf"); return ModelAssembly.forGltf(result.model(), List.of()); },
                    candidate -> ImportCoordinator.commitBuiltCandidate(candidate, () -> true,
                            prepared::commit, (built, committed) -> runtime.set(built), ModelAssembly::unloadRuntime));

            assertEquals(com.micaftic.morpher.core.storage.ImportCommitFlow.State.PUBLISHED, outcome.state());
        }

        ModelAssembly gltf = runtime.get();
        assertTrue(gltf.isGltf());
        assertTrue(gltf.isRuntimeResident());
        assertEquals(List.of("legacy", "gltf"), backendCalls);
        assertEquals("avatar", selection.selectedModelId());
        assertEquals("legacy", selection.selectedTextureId());
        assertTrue(Files.exists(customRoot.resolve("avatar.gltf")));
        assertFalse(Files.exists(customRoot.resolve("avatar.bbmodel")));
    }

    private static ModelAssembly legacyAssembly() {
        ModelResourceBundle resources = new ModelResourceBundle(
                Map.of(), new Object2ReferenceOpenHashMap<>(), new Object2ReferenceOpenHashMap<>(), Map.of());
        PlayerActionProvider previous = ModelActionProviderRegistry.get(ModelActionProfile.VANILLA_HUMANOID);
        ModelActionProviderRegistry.register(ModelActionProfile.VANILLA_HUMANOID,
                (modelBundle, resourceBundle) -> entity -> {});
        PlayerModelBundle bundle;
        try {
            bundle = new PlayerModelBundle((GeoModel) null, null,
                    new Object2ReferenceOpenHashMap<String, Animation>(),
                    new Object2ReferenceOpenHashMap<String, Animation>(), null, new ArmorConditions(),
                    new Object2ReferenceOpenHashMap<String, AnimationController>(),
                    new OrderedStringMap<>(new String[]{"legacy"}, new AbstractTexture[]{null}),
                    "legacy", null, resources, ModelSourceFormat.BBMODEL, ModelActionProfile.VANILLA_HUMANOID,
                    null, null);
        } finally {
            ModelActionProviderRegistry.register(ModelActionProfile.VANILLA_HUMANOID, previous);
        }
        return new ModelAssembly(bundle, Map.of(), Map.of(), resources, null,
                new ModelDisplayAssets(null, false, Map.of(), Map.of()), List.of());
    }

    @Test
    void slowerImportCannotOverwriteNewerAssemblyOrClearNextLease() throws Exception {
        Path customRoot = tempDir.resolve("superseded-import");
        LocalModelImportStore store = new LocalModelImportStore(customRoot);
        Path oldSource = store.persist("avatar", "avatar.ysm", "old-source".getBytes(StandardCharsets.UTF_8));
        GltfModel oldModel = assertInstanceOf(ParsedImport.GltfPayload.class,
                ImportCoordinator.parsePickedBytes("old.gltf",
                        "{\"asset\":{\"version\":\"2.0\",\"generator\":\"old\"}}".getBytes(StandardCharsets.UTF_8),
                        RawYsmModel::new).payload()).result().model();
        ModelAssembly oldAssembly = ModelAssembly.forGltf(oldModel, List.of());
        AtomicReference<ModelAssembly> published = new AtomicReference<>(oldAssembly);
        AtomicReference<ModelAssembly> slowCandidate = new AtomicReference<>();
        ModelSelectionState selection = new ModelSelectionState();
        selection.remember("avatar", "texture:old", true, false);
        com.micaftic.morpher.core.model.lifecycle.KeyedRequestLeaseRegistry<String> requests =
                new com.micaftic.morpher.core.model.lifecycle.KeyedRequestLeaseRegistry<>();
        var slowLease = requests.begin("avatar");
        CountDownLatch slowParserStarted = new CountDownLatch(1);
        CountDownLatch allowSlowParserToFinish = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        byte[] slowBytes = "{\"asset\":{\"version\":\"2.0\",\"generator\":\"slow-A\"}}"
                .getBytes(StandardCharsets.UTF_8);

        try {
            Future<com.micaftic.morpher.core.storage.ImportCommitFlow.Outcome<LocalModelImportStore.CommitResult>>
                    slowImport = worker.submit(() -> {
                        try (LocalModelImportStore.PreparedImport prepared =
                                     store.prepare("avatar", "avatar.gltf", slowBytes)) {
                            return ImportCoordinator.importPrepared(prepared,
                                    () -> {
                                        slowParserStarted.countDown();
                                        assertTrue(allowSlowParserToFinish.await(5, TimeUnit.SECONDS));
                                        return ImportCoordinator.parsePickedBytes("avatar.gltf", slowBytes, RawYsmModel::new);
                                    },
                                    raw -> { throw new AssertionError("glTF import must not use the legacy assembler"); },
                                    result -> {
                                        ModelAssembly candidate = ModelAssembly.forGltf(result.model(), List.of());
                                        slowCandidate.set(candidate);
                                        return candidate;
                                    },
                                    candidate -> ImportCoordinator.commitBuiltCandidate(candidate,
                                            () -> requests.isCurrent(slowLease), prepared::commit,
                                            (assembly, committed) -> published.set(assembly),
                                            ModelAssembly::unloadRuntime));
                        } finally {
                            requests.complete(slowLease);
                        }
                    });

            assertTrue(slowParserStarted.await(5, TimeUnit.SECONDS));
            assertTrue(requests.invalidate("avatar"));
            var fastLease = requests.begin("avatar");
            assertTrue(requests.isCurrent(fastLease));
            byte[] fastBytes = "{\"asset\":{\"version\":\"2.0\",\"generator\":\"fast-B\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            try (LocalModelImportStore.PreparedImport prepared = store.prepare("avatar", "avatar.gltf", fastBytes)) {
                var fastOutcome = ImportCoordinator.importPrepared(prepared,
                        () -> ImportCoordinator.parsePickedBytes("avatar.gltf", fastBytes, RawYsmModel::new),
                        raw -> { throw new AssertionError("glTF import must not use the legacy assembler"); },
                        result -> ModelAssembly.forGltf(result.model(), List.of()),
                        candidate -> ImportCoordinator.commitBuiltCandidate(candidate,
                                () -> requests.isCurrent(fastLease), prepared::commit,
                                (assembly, committed) -> published.set(assembly),
                                ModelAssembly::unloadRuntime));
                assertEquals(com.micaftic.morpher.core.storage.ImportCommitFlow.State.PUBLISHED, fastOutcome.state());
            }
            assertTrue(requests.complete(fastLease));

            var nextLease = requests.begin("avatar");
            allowSlowParserToFinish.countDown();
            var slowOutcome = slowImport.get(5, TimeUnit.SECONDS);

            assertEquals(com.micaftic.morpher.core.storage.ImportCommitFlow.State.SUPERSEDED_BEFORE_COMMIT,
                    slowOutcome.state());
            assertTrue(requests.isCurrent(nextLease), "the old task's finally block must not clear the next lease");
            assertEquals("fast-B", published.get().getGltfModel().generator());
            assertFalse(slowCandidate.get().isRuntimeResident(), "the superseded candidate must be released");
            assertFalse(Files.exists(oldSource));
            assertEquals(fastBytes.length, Files.size(customRoot.resolve("avatar.gltf")));
            assertEquals("avatar", selection.selectedModelId());
            assertEquals("texture:old", selection.selectedTextureId());
            assertTrue(requests.complete(nextLease));
        } finally {
            allowSlowParserToFinish.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void pickedGltfWithMissingExternalDependencyCannotReplaceExistingImport() throws Exception {
        Path customRoot = tempDir.resolve("custom");
        LocalModelImportStore store = new LocalModelImportStore(customRoot);
        Path existing = store.persist("avatar", "avatar.ysm", "old-source".getBytes(StandardCharsets.UTF_8));
        byte[] invalidGltf = """
                {"asset":{"version":"2.0"},"buffers":[{"uri":"missing.bin","byteLength":1}]}
                """.getBytes(StandardCharsets.UTF_8);

        try (LocalModelImportStore.PreparedImport prepared = store.prepare("avatar", "avatar.gltf", invalidGltf)) {
            assertThrows(Exception.class, () -> ImportCoordinator.parsePickedBytes("avatar.gltf", invalidGltf,
                    () -> { throw new AssertionError("glTF must not reach the legacy parser"); }));
            assertEquals("old-source", Files.readString(existing));
            assertFalse(Files.exists(customRoot.resolve("avatar.gltf")));
        }

        try (LocalModelImportStore.PreparedImport prepared = store.prepare("avatar", "avatar.gltf", invalidGltf)) {
            assertThrows(java.io.IOException.class, () -> ImportCoordinator.parseLocalGltf(prepared.path()));
            assertEquals("old-source", Files.readString(existing));
            assertFalse(Files.exists(customRoot.resolve("avatar.gltf")));
        }

        assertEquals("old-source", Files.readString(existing));
        assertFalse(Files.exists(customRoot.resolve("avatar.gltf")));
    }
}
