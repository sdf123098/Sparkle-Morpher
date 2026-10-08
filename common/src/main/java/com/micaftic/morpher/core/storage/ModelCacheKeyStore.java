package com.micaftic.morpher.core.storage;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;

/** Retains the legacy server_key field and file. A broken index is never replaced. */
public final class ModelCacheKeyStore {
    private ModelCacheKeyStore() {}
    public static synchronized byte[] loadOrCreate(Path index) throws IOException {
        Path absolute = index.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        Path lock = absolute.resolveSibling(absolute.getFileName() + ".lock");
        try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                var ignored = channel.lock()) {
            if (Files.exists(absolute)) return read(absolute);
            byte[] key = new byte[56]; new SecureRandom().nextBytes(key);
            String json = "{\"server_key\":\"" + Base64.getEncoder().encodeToString(key) + "\"}";
            Path temporary = Files.createTempFile(absolute.getParent(), "model-key-", ".tmp");
            try {
                Files.writeString(temporary, json, StandardCharsets.UTF_8);
                AtomicFileMover.moveWithRetry(temporary, absolute);
            } finally { Files.deleteIfExists(temporary); }
            return key;
        }
    }
    private static byte[] read(Path index) throws IOException {
        if (Files.size(index) > 1024 * 1024) throw new IOException("Model cache key index is too large; retained at " + index);
        String json = Files.readString(index, StandardCharsets.UTF_8);
        if (json.length() > 1024 * 1024) throw new IOException("Model cache key index is too large; retained at " + index);
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            reader.beginObject(); var fields = new HashSet<String>(); String encoded = null;
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (!fields.add(name)) throw new IOException("Duplicate cache key index field");
                if (name.equals("server_key")) {
                    if (reader.peek() != JsonToken.STRING) throw new IOException("Invalid legacy cache key");
                    encoded = reader.nextString();
                } else reader.skipValue();
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT || encoded == null) throw new IOException("Missing or invalid legacy cache key");
            byte[] key = Base64.getDecoder().decode(encoded);
            if (key.length != 56) throw new IOException("Invalid legacy cache key length");
            return key;
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw new IOException("Unreadable model cache key index; original retained at " + index, invalid);
        }
    }
}
