package com.micaftic.morpher.core.architectury.platform;

import net.neoforged.fml.ModContainer;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Minimal NeoForge-backed replacement for Architectury's Mod wrapper.
 */
public class Mod {
    private final String modId;
    private final ModContainer container;

    public Mod(String modId) {
        this.modId = modId;
        this.container = null;
    }

    Mod(ModContainer container) {
        this.modId = container.getModId();
        this.container = container;
    }

    public String getVersion() {
        return container != null ? container.getModInfo().getVersion().toString() : "unknown";
    }

    public String getName() {
        return container != null ? container.getModInfo().getDisplayName() : modId;
    }

    public String getModId() {
        return modId;
    }

    public Optional<Path> findResource(String... paths) {
        if (container == null) {
            return Optional.empty();
        }
        Path root = container.getModInfo().getOwningFile().getFile().getFilePath();
        Path resolved = root;
        for (String path : paths) {
            resolved = resolved.resolve(path);
        }
        return Optional.of(resolved);
    }
}
