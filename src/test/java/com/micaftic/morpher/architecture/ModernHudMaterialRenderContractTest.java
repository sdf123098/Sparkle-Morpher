package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for the 26.1.2 modern HUD OpenGL restoration. */
class ModernHudMaterialRenderContractTest {
    @Test
    void modernHudUsesTheProvenOpenGlBonePath() throws IOException {
        Path instance = findRepoFile(Path.of("src", "main", "java", "com", "micaftic",
                "morpher", "client", "renderer", "modernhud", "ModernHudRenderInstance.java"));
        Path matrixComputer = findRepoFile(Path.of("src", "main", "java", "com", "micaftic",
                "morpher", "core", "gpu", "BoneMatrixComputer.java"));
        Path shader = findRepoFile(Path.of("src", "main", "resources", "bone_skin.vsh"));

        String instanceSource = Files.readString(instance);
        String matrixSource = Files.readString(matrixComputer);
        String shaderSource = Files.readString(shader);

        assertTrue(instanceSource.contains("SmGraphicsBackendDetector.isRawOpenGlAllowed()"));
        assertTrue(instanceSource.contains("GpuMeshBuilder.build(geoModel)"));
        assertTrue(instanceSource.contains("BoneMatrixComputer.compute"));
        assertTrue(instanceSource.contains("GL43.GL_SHADER_STORAGE_BUFFER"));
        assertTrue(matrixSource.contains("144-byte SSBO"));
        assertTrue(shaderSource.contains("layout(std430, binding = 0) readonly buffer BoneBlock"));

        assertFalse(instanceSource.contains("GpuBufferSlice"));
        assertFalse(instanceSource.contains("Blaze3DBoneSkinPipeline"));
        assertFalse(instanceSource.contains("GpuBuffer.USAGE_UNIFORM"));
        assertFalse(instanceSource.contains("VertexFormatElement.register"));
        assertFalse(Files.exists(Path.of("src", "main", "java", "com", "micaftic",
                "morpher", "core", "gpu", "Blaze3DBoneSkinPipeline.java")));
    }

    private static Path findRepoFile(Path relative) throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(relative);
            if (Files.isRegularFile(candidate)) return candidate;
            directory = directory.getParent();
        }
        throw new IOException("Cannot locate modern HUD source: " + relative);
    }
}


