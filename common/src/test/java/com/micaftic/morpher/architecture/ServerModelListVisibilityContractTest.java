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
                Path.of("common/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"),
                Path.of("../common/src/main/java/com/micaftic/morpher/client/ClientModelManager.java"));
        String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
        int start = source.indexOf("public static Set<String> getAvailableModelIds()");
        int end = source.indexOf("\n    }", start);
        assertTrue(source.substring(start, end).contains("serverModels"));
    }

    private static Path firstExisting(Path... candidates) throws IOException {
        for (Path candidate : candidates) if (Files.isRegularFile(candidate)) return candidate;
        throw new IOException("ClientModelManager source not found");
    }
}
