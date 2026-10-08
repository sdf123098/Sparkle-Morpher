package com.micaftic.morpher.core.importing;

import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.resource.gltf.GltfLoader;
import com.micaftic.morpher.resource.gltf.GltfLoadResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

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
}
