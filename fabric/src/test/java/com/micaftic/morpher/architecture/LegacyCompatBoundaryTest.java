package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keeps the old handshake implementation behind the legacy compatibility boundary. */
class LegacyCompatBoundaryTest {
    private static final String LEGACY_STATE_IMPORT =
            "import com.micaftic.morpher.core.api.network.state.LegacySpmHandshakeState;";
    private static final String[] LEGACY_NETWORK_CALLS = {
            "LegacyModelProtocol.", "LegacyModelSyncProtocol.",
            "LegacyModelSyncClient.", "LegacyModelCacheClient.",
            "LegacyServerUploadTransport.", "LegacySyncFlowControl.",
            "YesModelUtils.",
            "import com.micaftic.morpher.core.legacy.",
            "import com.micaftic.morpher.network.LegacySyncFlowControl;",
            "import com.micaftic.morpher.model.LegacyModelSyncProtocol;",
            "import com.micaftic.morpher.network.protocol.LegacyModelProtocol;",
            "import com.micaftic.morpher.client.LegacyModelSyncClient;",
            "import com.micaftic.morpher.client.LegacyModelCacheClient;",
            "import com.micaftic.morpher.client.upload.LegacyServerUploadTransport;"
    };

    @Test
    void productionCodeDoesNotImportLegacyHandshakeStateDirectly() throws IOException {
        Path repository = locateRepository();
        List<String> violations = new ArrayList<>();
        for (Path sourceRoot : sourceRoots(repository)) {
            if (!Files.isDirectory(sourceRoot)) continue;
            try (Stream<Path> files = Files.walk(sourceRoot)) {
                files.filter(path -> path.toString().endsWith(".java")).forEach(file -> {
                    try {
                        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                        for (int i = 0; i < lines.size(); i++) {
                            if (lines.get(i).contains(LEGACY_STATE_IMPORT)) {
                                violations.add(repository.relativize(file) + ":" + (i + 1));
                            }
                            String normalizedPath = file.toString().replace('\\', '/');
                            String trimmed = lines.get(i).trim();
                            boolean legacyImplementation = file.getFileName() != null
                                    && file.getFileName().toString().startsWith("Legacy");
                            if (!normalizedPath.contains("/legacy/compat/")
                                    && !legacyImplementation
                                    && !trimmed.startsWith("//") && !trimmed.startsWith("*")
                                    && containsAny(trimmed, LEGACY_NETWORK_CALLS)) {
                                violations.add(repository.relativize(file) + ":" + (i + 1)
                                        + " legacy network call");
                            }
                        }
                    } catch (IOException exception) {
                        throw new BoundaryScanException(exception);
                    }
                });
            } catch (BoundaryScanException exception) {
                throw exception.getCause();
            }
        }
        assertTrue(violations.isEmpty(),
                "Legacy handshake state leaked into production imports: " + violations);
    }

    private static List<Path> sourceRoots(Path repository) {
        return List.of(
                repository.resolve("common/src/main/java"),
                repository.resolve("fabric/src/main/java"),
                repository.resolve("src/main/java"),
                repository.resolve("src/neoforge/java"));
    }

    private static boolean containsAny(String line, String[] needles) {
        for (String needle : needles) if (line.contains(needle)) return true;
        return false;
    }

    private static Path locateRepository() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null && !Files.isRegularFile(current.resolve(".github/workflows/ci.yml"))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("Could not locate repository root");
        return current;
    }

    private static final class BoundaryScanException extends RuntimeException {
        private BoundaryScanException(IOException cause) { super(cause); }
        @Override public synchronized IOException getCause() { return (IOException) super.getCause(); }
    }
}
