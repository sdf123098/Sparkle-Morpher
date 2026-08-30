package com.micaftic.morpher.resource.gltf;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registry for glTF extensions understood by the independent loader.
 *
 * <p>The core loader is strict by default. An extension becomes usable only
 * after a handler is registered; the handler may normalize extension data into
 * the core JSON representation or validate data needed by a richer model
 * implementation. Unknown object-level extensions are rejected as well, even
 * when an exporter forgot to list them in {@code extensionsUsed}.</p>
 */
public final class GltfExtensionRegistry {
    @FunctionalInterface
    public interface Handler {
        void apply(JsonObject root) throws GltfLoader.GltfParseException;
    }

    private final Map<String, Handler> handlers;

    private GltfExtensionRegistry(Map<String, Handler> handlers) {
        this.handlers = Map.copyOf(handlers);
    }

    /** Strict first-stage policy: no extensions are accepted. */
    public static GltfExtensionRegistry coreOnly() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    void apply(JsonObject root) throws GltfLoader.GltfParseException {
        Set<String> names = new LinkedHashSet<>();
        collectNames(root, names);
        Set<Handler> invoked = new LinkedHashSet<>();
        for (String name : names) {
            Handler handler = handlers.get(name);
            if (handler == null) {
                throw new GltfLoader.GltfParseException("glTF extension is not supported: " + name);
            }
            if (invoked.add(handler)) {
                handler.apply(root);
            }
        }
    }

    private void collectNames(JsonElement element, Set<String> result) throws GltfLoader.GltfParseException {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectNames(child, result);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        collectDeclaredNames(object.get("extensionsUsed"), result);
        collectDeclaredNames(object.get("extensionsRequired"), result);
        JsonElement extensions = object.get("extensions");
        if (extensions != null && !extensions.isJsonNull()) {
            if (!extensions.isJsonObject()) {
                throw new GltfLoader.GltfParseException("glTF property extensions must be an object");
            }
            for (String name : extensions.getAsJsonObject().keySet()) result.add(name);
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!entry.getKey().equals("extensionsUsed") && !entry.getKey().equals("extensionsRequired")
                    && !entry.getKey().equals("extensions")) {
                collectNames(entry.getValue(), result);
            }
        }
    }

    private static void collectDeclaredNames(JsonElement element, Set<String> result) throws GltfLoader.GltfParseException {
        if (element == null || element.isJsonNull()) return;
        if (!element.isJsonArray()) throw new GltfLoader.GltfParseException("glTF extensionsUsed/extensionsRequired must be arrays");
        for (JsonElement child : element.getAsJsonArray()) {
            if (!child.isJsonPrimitive() || !child.getAsJsonPrimitive().isString()) {
                throw new GltfLoader.GltfParseException("glTF extension names must be strings");
            }
            result.add(child.getAsString());
        }
    }

    public static final class Builder {
        private final Map<String, Handler> handlers = new LinkedHashMap<>();

        public Builder register(String name, Handler handler) {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Extension name must not be blank");
            if (handler == null) throw new NullPointerException("Extension handler must not be null");
            if (handlers.putIfAbsent(name, handler) != null) {
                throw new IllegalArgumentException("Extension handler already registered: " + name);
            }
            return this;
        }

        public GltfExtensionRegistry build() {
            return new GltfExtensionRegistry(new LinkedHashMap<>(handlers));
        }
    }
}
