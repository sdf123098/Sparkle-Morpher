package com.micaftic.morpher.resource.gltf;

import com.micaftic.morpher.client.renderer.gltf.GltfVertexConsumerRenderer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GltfTriangleTopologyTest {
    @Test
    void gltfRenderTypeFactoryUsesTriangles() throws Exception {
        Class<?> factory = Class.forName(
                "com.micaftic.morpher.client.renderer.gltf.GltfRenderTypes");
        Method topology = factory.getMethod("topology");

        assertEquals("TRIANGLES", topology.invoke(null).toString());
    }

    @Test
    void loaderRejectsNonTrianglePrimitiveModes() {
        String json = "{\"asset\":{\"version\":\"2.0\"},"
                + "\"meshes\":[{\"primitives\":[{\"mode\":5,"
                + "\"attributes\":{\"POSITION\":0}}]}],\"accessors\":[]}";

        assertThrows(GltfLoader.GltfParseException.class, () -> GltfLoader.load(
                json.getBytes(StandardCharsets.UTF_8), null, "non-triangle.gltf"));
    }

    @Test
    void rendererRejectsIncompleteTriangleIndexGroups() {
        assertEquals(6, GltfVertexConsumerRenderer.validateTriangleIndexCount(6));
        assertThrows(IllegalArgumentException.class,
                () -> GltfVertexConsumerRenderer.validateTriangleIndexCount(4));
    }
}
