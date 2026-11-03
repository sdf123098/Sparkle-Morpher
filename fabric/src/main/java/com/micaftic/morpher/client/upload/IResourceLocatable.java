package com.micaftic.morpher.client.upload;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public interface IResourceLocatable {
    Optional<ResourceLocation> getResourceLocation();

    /**
     * Nullable access for render hot paths that should not allocate Optional wrappers.
     */
    default ResourceLocation getResourceLocationOrNull() {
        return getResourceLocation().orElse(null);
    }
}
