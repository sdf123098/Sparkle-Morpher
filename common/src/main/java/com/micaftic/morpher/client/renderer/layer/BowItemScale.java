package com.micaftic.morpher.client.renderer.layer;

import org.joml.Matrix4f;

/** Bow scale correction relative to the transform inherited from the player renderer. */
final class BowItemScale {
    private BowItemScale() { }

    static float correction(Matrix4f pose, float inheritedScale) {
        float scale = largestAxisScale(pose);
        if (!Float.isFinite(inheritedScale) || inheritedScale < 1.0E-4f
                || !Float.isFinite(scale) || scale <= inheritedScale) {
            return 1.0f;
        }
        return inheritedScale / scale;
    }

    static float largestAxisScale(Matrix4f pose) {
        float x = length(pose.m00(), pose.m01(), pose.m02());
        float y = length(pose.m10(), pose.m11(), pose.m12());
        float z = length(pose.m20(), pose.m21(), pose.m22());
        return Math.max(x, Math.max(y, z));
    }

    private static float length(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }
}
