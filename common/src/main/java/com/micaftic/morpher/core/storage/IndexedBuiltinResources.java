package com.micaftic.morpher.core.storage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Loader-independent extraction from the index shipped in every artifact. */
public final class IndexedBuiltinResources {
    @FunctionalInterface public interface Source { InputStream open(String path) throws IOException; }
    private IndexedBuiltinResources() {}
    public static int extract(Path directory, String root, String indexPath, Source source) throws IOException {
        Path targetRoot = directory.toAbsolutePath().normalize();
        Files.createDirectories(targetRoot);
        if (Files.isSymbolicLink(targetRoot)) throw new IOException("Builtin root is a symbolic link");
        try (InputStream input = source.open(indexPath)) {
            if (input == null) return 0;
            String index = new String(input.readNBytes(1024 * 1024 + 1), StandardCharsets.UTF_8);
            if (index.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) throw new IOException("Builtin index too large");
            int count = 0;
            for (String line : index.split("\\R")) {
                String relative = line.trim();
                if (relative.isEmpty() || relative.startsWith("#")) continue;
                if (relative.indexOf('\\') >= 0 || relative.indexOf(':') >= 0 || Path.of(relative).isAbsolute())
                    throw new IOException("Invalid builtin index path");
                Path target = targetRoot.resolve(relative).normalize();
                if (!target.startsWith(targetRoot) || target.equals(targetRoot)) throw new IOException("Builtin index path escapes root");
                for (Path parent = target; !parent.equals(targetRoot); parent = parent.getParent())
                    if (Files.isSymbolicLink(parent)) throw new IOException("Builtin destination includes a symbolic link");
                try (InputStream bytes = source.open(root + relative)) {
                    if (bytes == null) throw new IOException("Missing indexed builtin resource: " + relative);
                    Files.createDirectories(target.getParent());
                    Path temporary = Files.createTempFile(target.getParent(), "builtin-", ".tmp");
                    try { Files.copy(bytes, temporary, StandardCopyOption.REPLACE_EXISTING); AtomicFileMover.moveWithRetry(temporary, target); }
                    finally { Files.deleteIfExists(temporary); }
                    count++;
                }
            }
            return count;
        }
    }
}
