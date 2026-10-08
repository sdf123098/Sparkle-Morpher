package com.micaftic.morpher.resource.gltf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GltfAnimationClockTest {
    @Test
    void convertsMinecraftTicksToSeconds() {
        assertEquals(1.0f, GltfAnimationClock.fromMinecraftTicks(20, 0.0f));
        assertEquals(1.025f, GltfAnimationClock.fromMinecraftTicks(20, 0.5f), 0.00001f);
    }

    @Test
    void clampsInvalidPartialTicks() {
        assertEquals(1.0f, GltfAnimationClock.fromMinecraftTicks(20, -2.0f));
        assertEquals(1.05f, GltfAnimationClock.fromMinecraftTicks(20, 5.0f), 0.00001f);
        assertEquals(1.0f, GltfAnimationClock.fromMinecraftTicks(20, Float.NaN));
    }
}
