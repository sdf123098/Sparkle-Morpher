package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModernHudHandItemRenderContractTest {
    @Test
    void handItemsStayInModernHudAttachmentPath() throws IOException {
        Path renderer = findRepoFile(
                Path.of("common", "src", "main", "java", "com", "micaftic", "morpher", "client",
                        "renderer", "modernhud", "ModernHudRenderer.java"),
                Path.of("src", "main", "java", "com", "micaftic", "morpher", "client",
                        "renderer", "modernhud", "ModernHudRenderer.java"));
        String source = Files.readString(renderer);

        assertTrue(source.contains("getMainHandItem()"));
        assertTrue(source.contains("getOffhandItem()"));
        assertTrue(source.contains("ModernHudHandItemLayout.locate"));
        assertFalse(source.contains("graphics.renderItem("));
        assertFalse(source.contains("graphics.item("));
        assertTrue(source.contains("graphics.entity("));
        assertTrue(source.contains("new ItemEntityRenderState"));

        Path overlay = findRepoFile(
                Path.of("common", "src", "main", "java", "com", "micaftic", "morpher", "client",
                        "renderer", "ExtraPlayerOverlay.java"),
                Path.of("src", "main", "java", "com", "micaftic", "morpher", "client",
                        "renderer", "ExtraPlayerOverlay.java"));
        String overlaySource = Files.readString(overlay);
        assertTrue(overlaySource.contains("ModernHudRenderer.render"));
        assertFalse(overlaySource.contains("!hasHandItem && ModernHudRenderer.render"));
    }

    @Test
    void modernHudConsumesWorldPoseBeforeUsingFallback() throws IOException {
        Path renderer = findRepoFile(
                Path.of("common", "src", "main", "java", "com", "micaftic", "morpher", "client",
                        "renderer", "modernhud", "ModernHudRenderer.java"),
                Path.of("src", "main", "java", "com", "micaftic", "morpher", "client",
                        "renderer", "modernhud", "ModernHudRenderer.java"));
        String source = Files.readString(renderer);

        assertTrue(source.contains("PlayerPoseSnapshot snapshot = ModernHudPoseStore.consume();"));
    }

    private static Path findRepoFile(Path... relativePaths) throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            for (Path relativePath : relativePaths) {
                Path candidate = directory.resolve(relativePath);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
            directory = directory.getParent();
        }
        throw new IOException("Repository file not found");
    }
}
