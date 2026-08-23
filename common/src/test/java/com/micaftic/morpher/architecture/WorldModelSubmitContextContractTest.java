package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract: deferred world geometry must replay with submit context restored. */
class WorldModelSubmitContextContractTest {

    @Test
    void deferredGeometryRestoresSubmitContextForBackendSelection() throws IOException {
        String source = Files.readString(findRepoFile(
                Path.of("common", "src", "main", "java", "com", "micaftic", "morpher", "geckolib3", "geo", "IGeoRenderer.java"),
                Path.of("src", "main", "java", "com", "micaftic", "morpher", "geckolib3", "geo", "IGeoRenderer.java")));

        assertTrue(source.contains("renderSubmittedGeometry(collector, buffer, pose"),
                "deferred geometry must retain the collector that recorded it");
        assertTrue(source.contains("SubmitNodeCollector previousSubmitContext = SubmitRenderContext.get();"),
                "deferred replay must preserve any outer submit context");
        assertTrue(source.contains("SubmitRenderContext.set(collector);"),
                "deferred replay must restore the recording collector before backend selection");
        assertTrue(source.contains("SubmitRenderContext.set(previousSubmitContext);"),
                "deferred replay must restore the previous context after rendering");
    }

    private static Path findRepoFile(Path... relativePaths) throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            for (Path relative : relativePaths) {
                Path candidate = directory.resolve(relative);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
            directory = directory.getParent();
        }
        throw new IOException("Cannot locate IGeoRenderer source");
    }
}
