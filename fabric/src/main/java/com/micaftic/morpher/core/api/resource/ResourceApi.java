package com.micaftic.morpher.core.api.resource;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Version-neutral resource identity. Minecraft resource objects stay inside adapters. */
public final class ResourceApi {
    private ResourceApi() {
    }

    public static ResourceId parse(String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        return separator < 0
                ? new ResourceId("minecraft", value)
                : new ResourceId(value.substring(0, separator), value.substring(separator + 1));
    }

    public static ResourceLocation nativeId(String namespace, String path) { return ResourceLocation.fromNamespaceAndPath(namespace, path); }
    public static ResourceLocation nativeId(ResourceId id) { return nativeId(id.namespace(), id.path()); }
    public static ResourceLocation parseNative(String value) { return ResourceLocation.tryParse(value); }

    public record ResourceId(String namespace, String path) {
        public ResourceId {
            if (namespace == null || namespace.isBlank() || path == null || path.isBlank()) {
                throw new IllegalArgumentException("Resource id requires namespace and path");
            }
        }

        public String asString() {
            return namespace + ":" + path;
        }
    }
}
