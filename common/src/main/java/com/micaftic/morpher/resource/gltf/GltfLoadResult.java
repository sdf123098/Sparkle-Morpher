package com.micaftic.morpher.resource.gltf;

import java.util.Objects;

/** Parsed glTF payload together with the exact external resources consumed by the parser. */
public record GltfLoadResult(GltfModel model, ResourceDependencyManifest dependencies) {
    public GltfLoadResult {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(dependencies, "dependencies");
    }
}
