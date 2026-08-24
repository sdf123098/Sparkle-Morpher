package com.micaftic.morpher.client.upload;

import net.minecraft.resources.Identifier;

import java.util.Optional;

public interface IResourceLocatable {
    Optional<Identifier> getResourceLocation();

    /**
     * Nullable access for render hot paths that should not allocate Optional wrappers.
     */
    default Identifier getResourceLocationOrNull() {
        return getResourceLocation().orElse(null);
    }
}