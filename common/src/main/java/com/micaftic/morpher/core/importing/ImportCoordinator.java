package com.micaftic.morpher.core.importing;

import com.micaftic.morpher.core.storage.ImportCommitFlow;
import com.micaftic.morpher.resource.gltf.GltfLoader;
import com.micaftic.morpher.resource.gltf.GltfLoadResult;
import com.micaftic.morpher.resource.pojo.RawYsmModel;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Coordinates source classification and format parsing into a typed import result. */
public final class ImportCoordinator {
    private ImportCoordinator() {}

    @FunctionalInterface
    public interface LegacyParser {
        RawYsmModel parse() throws Exception;
    }

    public static ParsedImport parsePickedBytes(String fileName, byte[] bytes, LegacyParser legacyParser) throws Exception {
        if (bytes == null) throw new IOException("Import source bytes are missing");
        Objects.requireNonNull(legacyParser, "legacyParser");
        ImportSource source = ImportSource.pickedBytes(fileName);
        if (source.isGltf()) {
            GltfLoadResult result = GltfLoader.loadWithManifest(bytes, null, fileName);
            return new ParsedImport(source, new ParsedImport.GltfPayload(result), List.of());
        }
        RawYsmModel model = legacyParser.parse();
        return new ParsedImport(source, new ParsedImport.LegacyRawPayload(model), List.of());
    }

    /** Parses a local glTF file with its explicitly authorized source directory. */
    public static ParsedImport parseLocalGltf(Path sourcePath) throws IOException {
        Objects.requireNonNull(sourcePath, "sourcePath");
        Path normalized = sourcePath.toAbsolutePath().normalize();
        Path root = normalized.getParent();
        if (root == null) throw new IOException("Local glTF source has no parent directory: " + sourcePath);
        ImportSource source = new ImportSource(normalized.getFileName().toString(),
                ImportSource.formatFromName(normalized.getFileName().toString()), ImportSource.Kind.LOCAL_PATH, root);
        if (!source.isGltf()) throw new IOException("Local glTF parser received a non-glTF source: " + sourcePath);
        GltfLoadResult result = GltfLoader.loadWithManifest(normalized);
        return new ParsedImport(source, new ParsedImport.GltfPayload(result), List.of());
    }

    /** Dispatches a typed parse result to exactly one runtime backend assembler. */
    public static <A> A buildCandidate(
            ParsedImport parsed,
            CandidateBuilder<RawYsmModel, A> legacyBuilder,
            CandidateBuilder<GltfLoadResult, A> gltfBuilder) throws Exception {
        Objects.requireNonNull(parsed, "parsed");
        Objects.requireNonNull(legacyBuilder, "legacyBuilder");
        Objects.requireNonNull(gltfBuilder, "gltfBuilder");
        if (parsed.payload() instanceof ParsedImport.LegacyRawPayload legacy) {
            return legacyBuilder.build(legacy.model());
        }
        if (parsed.payload() instanceof ParsedImport.GltfPayload gltf) {
            return gltfBuilder.build(gltf.result());
        }
        throw new IllegalArgumentException("Unsupported parsed import payload: " + parsed.payload().getClass().getName());
    }

    @FunctionalInterface
    public interface CandidateBuilder<T, A> {
        A build(T parsed) throws Exception;
    }

    /**
     * Commits the validated source, publishes the built candidate, and settles candidate ownership.
     * The candidate transfers to the runtime only after publication succeeds; every other outcome
     * releases it through the supplied runtime-specific adapter.
     */
    public static <A, S> ImportCommitFlow.Outcome<S> commitBuiltCandidate(
            A candidate,
            BooleanSupplier isCurrent,
            ImportCommitFlow.Committer<S> committer,
            CandidatePublisher<A, S> publisher,
            Consumer<A> releaseCandidate) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(isCurrent, "isCurrent");
        Objects.requireNonNull(committer, "committer");
        Objects.requireNonNull(publisher, "publisher");
        Objects.requireNonNull(releaseCandidate, "releaseCandidate");

        ImportCommitFlow.Outcome<S> outcome;
        try {
            outcome = ImportCommitFlow.commitThenPublish(isCurrent, committer,
                    committed -> publisher.publish(candidate, committed));
        } catch (RuntimeException | Error failure) {
            try {
                releaseCandidate.accept(candidate);
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        if (outcome.state() != ImportCommitFlow.State.PUBLISHED) {
            try {
                releaseCandidate.accept(candidate);
            } catch (RuntimeException cleanupFailure) {
                if (outcome.failure() != null) {
                    outcome.failure().addSuppressed(cleanupFailure);
                } else {
                    return new ImportCommitFlow.Outcome<>(ImportCommitFlow.State.FAILED_BEFORE_COMMIT,
                            null, cleanupFailure);
                }
            }
        }
        return outcome;
    }

    @FunctionalInterface
    public interface CandidatePublisher<A, S> {
        void publish(A candidate, S committedSource) throws Exception;
    }
}
