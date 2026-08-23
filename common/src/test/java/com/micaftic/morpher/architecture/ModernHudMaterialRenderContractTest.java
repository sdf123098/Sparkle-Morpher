package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for the 26.1.2 modern HUD translucent render pass. */
class ModernHudMaterialRenderContractTest {
    @Test
    void modernHudKeepsTheTranslucentPassFromTheWorldRenderer() throws IOException {
        Path instance = findRepoFile(
                Path.of("common", "src", "main", "java", "com", "micaftic", "morpher", "client",
                        "renderer", "modernhud", "ModernHudRenderInstance.java"),
                Path.of("src", "main", "java", "com", "micaftic", "morpher", "client",
                        "renderer", "modernhud", "ModernHudRenderInstance.java"));
        Path pipeline = findRepoFile(
                Path.of("common", "src", "main", "java", "com", "micaftic", "morpher", "core", "gpu",
                        "Blaze3DBoneSkinPipeline.java"),
                Path.of("src", "main", "java", "com", "micaftic", "morpher", "core", "gpu",
                        "Blaze3DBoneSkinPipeline.java"));
        Path fragment = findRepoFile(
                Path.of("common", "src", "main", "resources", "assets", "sparkle_morpher", "shaders",
                        "core", "blaze3d_bone_skin_translucent.fsh"),
                Path.of("src", "main", "resources", "assets", "sparkle_morpher", "shaders", "core",
                        "blaze3d_bone_skin_translucent.fsh"));

        String instanceSource = Files.readString(instance);
        String pipelineSource = Files.readString(pipeline);
        String fragmentSource = Files.readString(fragment);

        assertTrue(instanceSource.contains("geoModel.isTranslucentTexture(0)"));
        assertTrue(instanceSource.contains("TRANSLUCENT_PIPELINE"));
        assertTrue(instanceSource.contains("translucentDrawCount"));
        assertTrue(pipelineSource.contains("BlendFunction.TRANSLUCENT"));
        assertTrue(fragmentSource.contains("color.a < 0.1"));
        assertTrue(!fragmentSource.contains("color.a < 0.99"));
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
        throw new IOException("Cannot locate modern HUD material source");
    }
}
