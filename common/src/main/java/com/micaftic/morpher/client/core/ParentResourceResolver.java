package com.micaftic.morpher.client.core;

import dev.spm.core.api.ResourceKey;
import dev.spm.core.api.ResourceCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Explicit local model's canonical parent is the whole authorization scope; never follows escapes. */
public final class ParentResourceResolver implements ResourceCatalog, AutoCloseable {
    private final Path root;
    private final Path model;
    private final java.util.Set<ResourceKey> entries;
    private boolean closed;
    public ParentResourceResolver(Path model) throws IOException {
        this.model = model.toRealPath();
        if (!Files.isRegularFile(this.model)) throw new IOException("Core model must be a regular file");
        root = this.model.getParent();
        if (root == null) throw new IOException("Core model has no parent domain");
        var catalog = new java.util.HashSet<ResourceKey>();
        try (var paths = Files.walk(root, 32)) {
            var iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path resource = iterator.next();
                if (!Files.isRegularFile(resource, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
                if (!resource.toRealPath().startsWith(root)) throw new IOException("Resource escaped Core model catalog");
                catalog.add(new ResourceKey(root.relativize(resource).toString().replace('\\', '/')));
                if (catalog.size() > 16384) throw new IOException("Core local model domain exceeds 16384 files");
            }
        }
        entries = java.util.Set.copyOf(catalog);
    }
    public ResourceKey entry() { return new ResourceKey(model.getFileName().toString()); }
    @Override public synchronized java.util.Set<ResourceKey> entries() {
        if (closed) throw new IllegalStateException("Core model resource owner is closed");
        return entries;
    }
    @Override public synchronized InputStream open(ResourceKey key) throws IOException {
        if (closed) throw new IOException("Core model resource owner is closed");
        if (!entries.contains(key)) throw new IOException("Resource is not in the frozen Core model catalog");
        Path resource = root.resolve(key.path()).normalize().toRealPath();
        if (!resource.startsWith(root) || !Files.isRegularFile(resource)) throw new IOException("Resource escaped Core model domain");
        return Files.newInputStream(resource);
    }
    @Override public synchronized void close() { closed = true; }
}
