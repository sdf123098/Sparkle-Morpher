package com.micaftic.morpher.core.importing;

import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.core.storage.LocalModelImportStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

        assertEquals("old-source", Files.readString(existing));
        assertFalse(Files.exists(customRoot.resolve("avatar.gltf")));
    }
}
