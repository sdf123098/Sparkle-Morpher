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
        Path source = Path.of("../src/neoforge/java/com/micaftic/morpher/client/ClientModelManager.java");
        String text = Files.readString(source, StandardCharsets.UTF_8);
        int start = text.indexOf("public static Set<String> getAvailableModelIds()");
        int end = text.indexOf("\n    }", start);
        assertTrue(text.substring(start, end).contains("serverModels"));
    }
}
