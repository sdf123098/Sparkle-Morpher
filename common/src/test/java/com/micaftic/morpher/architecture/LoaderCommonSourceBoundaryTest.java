package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Enforces the R12.3 common-source loader boundary. */
class LoaderCommonSourceBoundaryTest {
    private static final Pattern LOADER_IMPORT = Pattern.compile("^\\s*import\\s+(?:net\\.fabricmc|net\\.neoforged|net\\.minecraftforge)\\.");

    @Test
    void commonSourceSetDoesNotImportLoaderApis() throws IOException {
        Path repository = locateRepository();
        // Fa1.21.1 is the historical Architectury multi-module exception: its common module
        // intentionally exposes Fabric environment annotations and the Forge config port.
        if (Files.isRegularFile(repository.resolve("common/build.gradle"))) return;

        Path common = repository.resolve("common/src/main/java");
        if (!Files.isDirectory(common)) return;

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(common)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(file -> {
                try {
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        if (LOADER_IMPORT.matcher(lines.get(i)).find()) {
                            violations.add(repository.relativize(file) + ":" + (i + 1) + " " + lines.get(i).trim());
                        }
                    }
                } catch (IOException exception) {
                    throw new BoundaryScanException(exception);
                }
            });
        } catch (BoundaryScanException exception) {
            throw exception.getCause();
        }

        assertTrue(violations.isEmpty(), "Loader API imports leaked into common source: " + violations);
    }

    private static Path locateRepository() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null && !Files.isRegularFile(current.resolve(".github/workflows/ci.yml"))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("Could not locate repository root from test working directory");
        return current;
    }

    private static final class BoundaryScanException extends RuntimeException {
        private BoundaryScanException(IOException cause) { super(cause); }

        @Override
        public synchronized IOException getCause() { return (IOException) super.getCause(); }
    }
}
