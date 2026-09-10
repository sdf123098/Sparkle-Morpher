package com.micaftic.morpher.core.gpu;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression contract: roulette geometry must be recorded by GuiGraphicsExtractor.
 *
 * <p>The legacy immediate-draw helpers ({@code PieShader}, {@code PiePortableRenderPath})
 * were removed as dead code in 1.2.5, so this guard now only needs to catch a
 * reintroduced raw OpenGL draw or a lost extractor-recorded fallback.</p>
 */
class RouletteRenderPathContractTest {

    @Test
    void pieDoesNotSubmitImmediateGpuCommandsDuringExtraction() throws IOException {
        String source = Files.readString(findRepoFile(
                Path.of("common", "src", "main", "java", "com", "micaftic", "morpher", "core", "gpu", "Pie.java"),
                Path.of("src", "main", "java", "com", "micaftic", "morpher", "core", "gpu", "Pie.java")));

        assertFalse(source.contains("GL11.glDrawArrays"),
                "Pie must not issue Raw OpenGL draw calls from extractRenderState");
        assertTrue(source.contains("drawFallback(graphics, centerX, centerY, innerRadius, outerRadius, startAngle, endAngle, rgba)"),
                "Pie must retain the extractor-recorded fallback path");
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
        throw new IOException("Cannot locate roulette Pie source");
    }
}
