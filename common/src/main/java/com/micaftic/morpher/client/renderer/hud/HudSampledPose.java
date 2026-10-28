package com.micaftic.morpher.client.renderer.hud;

/** An owned HUD pose; other render passes may freely overwrite the live model arrays. */
final class HudSampledPose {
    private Object owner;
    private Object model;
    private float[] matrices = new float[0];
    private float[] pivots = new float[0];
    private float headBaseX;
    private float headBaseY;
    private boolean valid;

    boolean capture(Object owner, Object model, float[] sourceMatrices, float[] sourcePivots,
                    float headBaseX, float headBaseY) {
        if (owner == null || model == null || sourceMatrices == null || sourcePivots == null
                || sourceMatrices.length == 0) {
            invalidate();
            return false;
        }
        if (matrices.length != sourceMatrices.length) matrices = new float[sourceMatrices.length];
        if (pivots.length != sourcePivots.length) pivots = new float[sourcePivots.length];
        System.arraycopy(sourceMatrices, 0, matrices, 0, sourceMatrices.length);
        System.arraycopy(sourcePivots, 0, pivots, 0, sourcePivots.length);
        this.owner = owner;
        this.model = model;
        this.headBaseX = headBaseX;
        this.headBaseY = headBaseY;
        valid = true;
        return true;
    }

    boolean matches(Object owner, Object model, int matrixLength, int pivotLength) {
        return valid && this.owner == owner && this.model == model
                && matrices.length == matrixLength && pivots.length == pivotLength;
    }

    boolean restore(Object owner, Object model, float[] destinationMatrices, float[] destinationPivots) {
        if (destinationMatrices == null || destinationPivots == null
                || !matches(owner, model, destinationMatrices.length, destinationPivots.length)) {
            return false;
        }
        System.arraycopy(matrices, 0, destinationMatrices, 0, matrices.length);
        System.arraycopy(pivots, 0, destinationPivots, 0, pivots.length);
        return true;
    }

    boolean owns(Object owner) {
        return valid && this.owner == owner;
    }

    float headBaseX() { return headBaseX; }
    float headBaseY() { return headBaseY; }

    void invalidate() {
        valid = false;
        owner = null;
        model = null;
    }
}
