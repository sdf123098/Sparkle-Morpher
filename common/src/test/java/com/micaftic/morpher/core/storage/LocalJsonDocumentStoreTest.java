package com.micaftic.morpher.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LocalJsonDocumentStoreTest {
    @TempDir Path directory;

    @Test void legacyLayoutAndOtherAccountsSurviveLocalPreferenceEdit() throws Exception {
        Path file = directory.resolve("favorites.json");
        Files.writeString(file, "{\"other-player\":{\"models\":[\"旧模型\"]},\"metadata\":null}");
        JsonObject root = LocalJsonDocumentStore.readObject(file);
        root.addProperty("new-setting", 7);
        LocalJsonDocumentStore.saveObject(file, root);
        JsonObject saved = LocalJsonDocumentStore.readObject(file);
        assertEquals("旧模型", saved.getAsJsonObject("other-player").getAsJsonArray("models").get(0).getAsString());
        assertEquals(7, saved.get("new-setting").getAsInt());
        assertTrue(saved.get("metadata").isJsonNull());
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test void damagedDocumentsCannotBeOverwrittenAfterLoadFallback() throws Exception {
        Path file = directory.resolve("favorites.json");
        for (String text : new String[]{"", "[]", "{", "{}{}", "{\"nested\":{\"name\":1,\"na\\u006de\":2}}", "{\"x\":NaN}"}) {
            byte[] original = text.getBytes(StandardCharsets.UTF_8);
            Files.write(file, original);
            assertThrows(java.io.IOException.class, () -> LocalJsonDocumentStore.readObject(file));
            assertThrows(java.io.IOException.class, () -> LocalJsonDocumentStore.saveObject(file, new JsonObject()));
            assertArrayEquals(original, Files.readAllBytes(file));
        }
        Files.write(file, new byte[]{(byte)0xff});
        assertThrows(java.io.IOException.class, () -> LocalJsonDocumentStore.saveObject(file, new JsonObject()));
        assertArrayEquals(new byte[]{(byte)0xff}, Files.readAllBytes(file));
    }

    @Test void oversizeDocumentsArePreservedAndMissingFilesCanBeCreated() throws Exception {
        Path file = directory.resolve("settings.json");
        var object = new JsonObject(); object.addProperty("models", "first");
        LocalJsonDocumentStore.saveObject(file, object);
        assertEquals("first", LocalJsonDocumentStore.readObject(file).get("models").getAsString());
        byte[] original = new byte[4 * 1024 * 1024 + 1];
        Files.write(file, original);
        assertThrows(java.io.IOException.class, () -> LocalJsonDocumentStore.saveObject(file, object));
        assertArrayEquals(original, Files.readAllBytes(file));
    }
}
