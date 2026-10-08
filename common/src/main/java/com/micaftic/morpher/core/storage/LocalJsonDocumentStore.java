package com.micaftic.morpher.core.storage;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashSet;

/** Preserves legacy local JSON layouts while refusing to overwrite unreadable documents. */
public final class LocalJsonDocumentStore {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private LocalJsonDocumentStore() {}

    public static JsonObject readObject(Path path) throws IOException {
        JsonElement root = readDocument(path);
        if (!root.isJsonObject()) throw new IOException("Local JSON must contain an object");
        return root.getAsJsonObject();
    }

    public static JsonArray readArray(Path path) throws IOException {
        if (!Files.exists(path)) return new JsonArray();
        JsonElement root = readDocument(path);
        if (!root.isJsonArray()) throw new IOException("Local JSON must contain an array");
        return root.getAsJsonArray();
    }

    private static JsonElement readDocument(Path path) throws IOException {
        if (!Files.exists(path)) return new JsonObject();
        byte[] bytes;
        try (var stream = Files.newInputStream(path)) { bytes = stream.readNBytes(MAX_BYTES + 1); }
        if (bytes.length > MAX_BYTES) throw new IOException("Local JSON exceeds its byte budget");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            try (var reader = new JsonReader(new StringReader(text))) {
                reader.setLenient(false);
                JsonElement root = readValue(reader, 0);
                if (reader.peek() != JsonToken.END_DOCUMENT)
                    throw new IOException("Local JSON must contain one document");
                return root;
            }
        } catch (RuntimeException error) { throw new IOException("Local JSON is invalid", error); }
    }

    private static JsonElement readValue(JsonReader reader, int depth) throws IOException {
        if (depth > 32) throw new IOException("Local JSON is nested too deeply");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject();
                var names = new HashSet<String>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!names.add(name)) throw new IOException("Duplicate local JSON key");
                    object.add(name, readValue(reader, depth + 1));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(readValue(reader, depth + 1));
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IOException("Invalid local JSON value");
        };
    }

    public static synchronized void saveObject(Path path, JsonObject object) throws IOException {
        // A load failure must never turn a subsequent UI save into a destructive reset.
        readObject(path);
        byte[] bytes = object.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Local JSON exceeds its byte budget");
        Path target = path.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".local-json-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException error) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
