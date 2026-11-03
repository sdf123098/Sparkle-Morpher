package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeSimdConfigCompatibilityContractTest {
    @Test
    void onePointTwoOneOneNativeSimdDependenciesArePresent() throws IOException {
        String config = Files.readString(find(Path.of(
                "common", "src", "main", "java", "com", "micaftic", "morpher",
                "config", "GeneralConfig.java")));
        assertTrue(config.contains("enum NativeSimdPolicy"));
        assertTrue(config.contains("NATIVE_SIMD_POLICY"));
        assertTrue(config.contains("safeGet(ForgeConfigSpec.EnumValue"));

        String log = Files.readString(find(Path.of(
                "common", "src", "main", "java", "com", "micaftic", "morpher",
                "core", "gpu", "GpuDebugLog.java")));
        assertTrue(log.contains("static void error"));
    }

    private static Path find(Path relative) throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IOException("Repository file not found: " + relative);
    }
}
