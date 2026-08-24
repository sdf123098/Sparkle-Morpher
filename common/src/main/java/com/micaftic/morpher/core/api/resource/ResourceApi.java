package com.micaftic.morpher.core.api.resource;

import java.util.Objects;
import net.minecraft.resources.Identifier;

/** Version-neutral resource identity. Minecraft resource objects stay inside adapters. */
public final class ResourceApi {
    private ResourceApi() { }
    public static ResourceId parse(String value) { Objects.requireNonNull(value, "value"); int separator = value.indexOf(':'); return separator < 0 ? new ResourceId("minecraft", value) : new ResourceId(value.substring(0, separator), value.substring(separator + 1)); }
    public static Identifier nativeId(String namespace, String path) { return Identifier.fromNamespaceAndPath(namespace, path); }
    public static Identifier nativeId(ResourceId id) { return nativeId(id.namespace(), id.path()); }
    public static Identifier parseNative(String value) { return Identifier.tryParse(value); }
    public record ResourceId(String namespace, String path) { public ResourceId { if (namespace == null || namespace.isBlank() || path == null || path.isBlank()) throw new IllegalArgumentException("Resource id requires namespace and path"); } public String asString() { return namespace + ":" + path; } }
}
