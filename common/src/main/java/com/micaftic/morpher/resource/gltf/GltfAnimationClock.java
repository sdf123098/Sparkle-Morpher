package com.micaftic.morpher.resource.gltf;

/** Converts Minecraft's tick-based render clock to glTF animation seconds. */
public final class GltfAnimationClock {
    private static final float TICKS_PER_SECOND = 20.0f;

    private GltfAnimationClock() {
    }

    public static float fromMinecraftTicks(int tickCount, float partialTick) {
        float safePartialTick = Float.isFinite(partialTick)
                ? Math.max(0.0f, Math.min(1.0f, partialTick))
                : 0.0f;
        return (tickCount + safePartialTick) / TICKS_PER_SECOND;
    }
}
