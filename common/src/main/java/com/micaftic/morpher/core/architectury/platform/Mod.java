package com.micaftic.morpher.core.architectury.platform;

import net.fabricmc.loader.api.ModContainer;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Minimal Fabric-backed replacement for Architectury's Mod wrapper.
 */
public class Mod {
    private final String modId;
    private final ModContainer container;

    public Mod(String modId) {
        this.modId = modId;
        this.container = null;
    }

    Mod(ModContainer container) {
        this.modId = container.getMetadata().getId();
        this.container = container;
    }

    public String getVersion() {
        return container != null ? container.getMetadata().getVersion().getFriendlyString() : "unknown";
    }

    public String getName() {
        return container != null ? container.getMetadata().getName() : modId;
    }

    public String getModId() {
        return modId;
    }

    public Optional<Path> findResource(String... paths) {
        if (container == null) {
            return Optional.empty();
        }
        return container.findPath(String.join("/", paths));
    }
}
