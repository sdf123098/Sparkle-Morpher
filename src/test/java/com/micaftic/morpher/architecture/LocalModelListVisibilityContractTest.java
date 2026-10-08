package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalModelListVisibilityContractTest {
    @Test
    void availableModelIdsIncludesLocalCatalogEntriesBeforeAssemblyIsReady() throws IOException {
        Path sourcePath = firstExisting(
                Path.of("fabric/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("common/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("src/neoforge/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("../fabric/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("../src/neoforge/java/com/micaftic/morpher/client/ClientModelManager.java"));
        String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
        int methodStart = source.indexOf("public static Set<String> getAvailableModelIds()");
        int methodEnd = source.indexOf("\n    }", methodStart);
        String method = source.substring(methodStart, methodEnd);
        assertTrue(method.contains("lazyModelSources"));
        assertFalse(method.contains("serverModels"));
    }

    private static Path firstExisting(Path... candidates) throws IOException {
        for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
            for (Path candidate : candidates) {
                Path source = base.resolve(candidate).normalize();
                if (Files.isRegularFile(source)) return source;
            }
        }
        throw new IOException("ClientModelManager source not found");
    }
}
