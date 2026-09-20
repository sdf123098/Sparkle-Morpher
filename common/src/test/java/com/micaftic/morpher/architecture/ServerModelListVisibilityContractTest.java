package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerModelListVisibilityContractTest {
    @Test
    void availableModelIdsIncludesServerCatalogEntriesBeforeCacheIsReady() throws IOException {
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
        assertTrue(method.contains("serverModels"));
    }

    private static Path firstExisting(Path... candidates) throws IOException {
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IOException("ClientModelManager source not found");
    }
}
