package com.micaftic.morpher.resource.gltf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GltfHandNodeResolverTest {
    @Test
    void prefersHandEndpointNamesForNetEaseModel() {
        GltfModel.Node root = node("root", -1, List.of(1, 2, 3, 4));
        GltfModel.Node rightArm = node("rightArm", 0, List.of());
        GltfModel.Node rightArmDown = node("rightArmDown", 0, List.of());
        GltfModel.Node leftArm = node("leftArm", 0, List.of());
        GltfModel.Node leftArmDown = node("leftArmDown", 0, List.of());
        GltfModel model = new GltfModel(null,
                List.of(new GltfModel.Scene("scene", List.of(0))), 0,
                List.of(root, rightArm, rightArmDown, leftArm, leftArmDown),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        assertEquals(2, GltfHandNodeResolver.find(model, GltfHandNodeResolver.Hand.RIGHT));
        assertEquals(4, GltfHandNodeResolver.find(model, GltfHandNodeResolver.Hand.LEFT));
    }

    private static GltfModel.Node node(String name, int parent, List<Integer> children) {
        return new GltfModel.Node(name, parent, children, -1, -1,
                new float[]{0, 0, 0}, new float[]{0, 0, 0, 1}, new float[]{1, 1, 1}, null);
    }
}
