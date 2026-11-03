package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SlashBladeModelAttachmentContractTest {
    @Test
    void mainHandBladeUsesModelLocatorsBeforeEntitySpaceMmdFallback() throws IOException {
        Path facade = findRepoFile(Path.of("common", "src", "main", "java", "com", "micaftic", "morpher",
                "core", "compat", "slashblade", "SlashBladeRenderer.java"));
        Path bridge = findRepoFile(Path.of("src", "neoforge", "java", "com", "micaftic", "morpher",
                "core", "compat", "slashblade", "SlashBladeBridge.java"));
        String facadeSource = Files.readString(facade);
        String bridgeSource = Files.readString(bridge);

        assertTrue(facadeSource.contains("renderMainHandBlade(entity, model"));
        assertTrue(bridgeSource.contains("model.bladeBones()"));
        assertTrue(bridgeSource.contains("model.leftHandBones()"));
        assertTrue(bridgeSource.contains("model.sheathBones()"));
        assertTrue(bridgeSource.contains("RenderUtils.prepMatrixForLocator"));
        assertTrue(bridgeSource.contains("poseStack.scale((float) MODEL_SCALE_BASE"));
    }

    private static Path findRepoFile(Path relativePath) throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IOException("Repository file not found: " + relativePath);
    }
}
