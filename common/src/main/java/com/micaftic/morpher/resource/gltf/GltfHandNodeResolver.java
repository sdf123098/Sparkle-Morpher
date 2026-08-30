package com.micaftic.morpher.resource.gltf;

import java.util.List;
import java.util.Locale;

/** Finds a stable glTF node to use as a third-person item attachment point. */
public final class GltfHandNodeResolver {
    public enum Hand { LEFT, RIGHT }

    private GltfHandNodeResolver() {
    }

    public static int find(GltfModel model, Hand hand) {
        if (model == null || hand == null) {
            return -1;
        }
        List<String> aliases = hand == Hand.LEFT
                ? List.of("lefthand", "handleft", "handl", "leftarmdown", "leftforearm", "leftarm")
                : List.of("righthand", "handright", "handr", "rightarmdown", "rightforearm", "rightarm");

        // Prefer non-mesh skeleton nodes. Mesh attachment nodes with similar names
        // are valid fallbacks, but their local origin is often not the hand joint.
        for (boolean skeletonOnly : new boolean[]{true, false}) {
            for (String alias : aliases) {
                for (int i = 0; i < model.nodes().size(); i++) {
                    GltfModel.Node node = model.nodes().get(i);
                    if ((!skeletonOnly || node.meshIndex() < 0) && matches(node.name(), alias)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private static boolean matches(String name, String alias) {
        String normalizedName = normalize(name);
        String normalizedAlias = normalize(alias);
        return !normalizedName.isEmpty()
                && (normalizedName.equals(normalizedAlias) || normalizedName.contains(normalizedAlias));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
