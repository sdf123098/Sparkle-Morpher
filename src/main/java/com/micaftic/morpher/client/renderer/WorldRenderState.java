package com.micaftic.morpher.client.renderer;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class WorldRenderState {
    private static final Matrix4f projectionMatrix = new Matrix4f();
    private static boolean hasProjectionMatrix;

    private WorldRenderState() {
    }

    public static void begin(Matrix4fc projectionMatrix) {
        if (projectionMatrix == null) {
            hasProjectionMatrix = false;
            return;
        }
        WorldRenderState.projectionMatrix.set(projectionMatrix);
        hasProjectionMatrix = true;
    }

    public static void end() {
        hasProjectionMatrix = false;
    }

    public static boolean getProjectionMatrix(Matrix4f target) {
        if (!hasProjectionMatrix) {
            return false;
        }
        target.set(projectionMatrix);
        return true;
    }
}
