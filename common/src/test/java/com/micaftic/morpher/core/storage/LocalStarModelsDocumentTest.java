package com.micaftic.morpher.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LocalStarModelsDocumentTest {
    @TempDir
    Path directory;

    @Test
    void migratesLegacyFavoritesOnceAndKeepsOtherScopes() throws Exception {
        Path current = directory.resolve("local_star_models.json");
        Path legacy = directory.resolve("local_starred_models.json");
        String scope = "server.example|player-a";
        Files.writeString(current, "{\"other|player-b\":{\"models\":[\"kept\"]},"
                + "\"server.example|player-a\":{\"models\":[\"existing\"]}}");
        Files.writeString(legacy, "[\"legacy\",\"existing\",\"legacy\",\" \",4]");

        assertEquals(Set.of("existing", "legacy"), LocalStarModelsDocument.load(current, legacy, scope));
        assertTrue(Files.exists(legacy), "migration must retain the original file");
        assertEquals(Set.of("kept"), LocalStarModelsDocument.load(current, legacy, "other|player-b"));

        LocalStarModelsDocument.save(current, legacy, scope, Set.of("existing"));
        Files.writeString(legacy, "[\"must-not-be-imported-again\"]");
        assertEquals(Set.of("existing"), LocalStarModelsDocument.load(current, legacy, scope));
    }

    @Test
    void rejectsUnresolvedScopeWithoutCreatingAnUnknownPartition() {
        Path current = directory.resolve("local_star_models.json");
        Path legacy = directory.resolve("local_starred_models.json");
        assertThrows(IllegalArgumentException.class,
                () -> LocalStarModelsDocument.load(current, legacy, "server|unknown"));
        assertFalse(Files.exists(current));
    }

    @Test
    void malformedLegacyFileIsPreservedAndDoesNotMarkMigrationComplete() throws Exception {
        Path current = directory.resolve("local_star_models.json");
        Path legacy = directory.resolve("local_starred_models.json");
        byte[] damaged = "{not-an-array}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(legacy, damaged);

        assertThrows(IOException.class,
                () -> LocalStarModelsDocument.load(current, legacy, "server|player"));
        assertFalse(Files.exists(current));
        assertArrayEquals(damaged, Files.readAllBytes(legacy));
    }

    @Test
    void damagedCurrentFileCannotBeReadOrOverwritten() throws Exception {
        Path current = directory.resolve("local_star_models.json");
        Path legacy = directory.resolve("local_starred_models.json");
        byte[] damaged = "{\"scope\": [}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(current, damaged);

        assertThrows(IOException.class,
                () -> LocalStarModelsDocument.load(current, legacy, "server|player"));
        assertThrows(IOException.class,
                () -> LocalStarModelsDocument.save(current, legacy, "server|player", Set.of("new")));
        assertArrayEquals(damaged, Files.readAllBytes(current));
    }
}
