package com.micaftic.morpher.client.render;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Mutable per-frame bone pose view into {@link ModelScenePose}'s flat matrix/pivot buffers,
 * replacing legacy {@code AnimatedGeoBone} / {@code IBone}.
 */
public final class ModelBoneView {
    private static final int MATRIX_STRIDE = 12;
    private static final int PIVOT_STRIDE = 4;

    private final ModelBone definition;
    private final int index;
    private final float[] matrixData;
    private final int matrixOffset;
    private final float[] absPivotData;
    private final int pivotOffset;
    private final Vector3f initialRotation;
    private final Vector3f initialPosition;
    private final Vector3f initialScale;
    private @Nullable ModelBoneView parent;

    public ModelBoneView(
            ModelBone definition,
            int index,
            float[] matrixData,
            int matrixOffset,
            float[] absPivotData,
            int pivotOffset) {
        this.definition = definition;
        this.index = index;
        this.matrixData = matrixData;
        this.matrixOffset = matrixOffset;
        this.absPivotData = absPivotData;
        this.pivotOffset = pivotOffset;
        this.initialRotation = new Vector3f(definition.getRotX(), definition.getRotY(), definition.getRotZ());
        this.initialPosition = new Vector3f(0.0f, 0.0f, 0.0f);
        this.initialScale = new Vector3f(1.0f, 1.0f, 1.0f);
        resetToBind();
    }

    public void resetToBind() {
        int o = this.matrixOffset;
        this.matrixData[o] = this.initialRotation.x;
        this.matrixData[o + 1] = this.initialRotation.y;
        this.matrixData[o + 2] = this.initialRotation.z;
        this.matrixData[o + 3] = 0.0f;
        this.matrixData[o + 4] = 0.0f;
        this.matrixData[o + 5] = 0.0f;
        this.matrixData[o + 6] = 1.0f;
        this.matrixData[o + 7] = 1.0f;
        this.matrixData[o + 8] = 1.0f;
        this.matrixData[o + 9] = this.definition.isHidden() || this.definition.cubesAreHidden() ? 1.0f : 0.0f;
        this.matrixData[o + 10] = this.definition.isHidden() || this.definition.childBonesAreHiddenToo() ? 1.0f : 0.0f;
        this.matrixData[o + 11] = 0.0f;
    }

    public ModelBone getDefinition() {
        return this.definition;
    }

    public int getIndex() {
        return this.index;
    }

    public String getName() {
        return this.definition.getName();
    }

    public int getBoneId() {
        return this.definition.getBoneId();
    }

    public @Nullable ModelBoneView getParent() {
        return this.parent;
    }

    void setParent(@Nullable ModelBoneView parent) {
        this.parent = parent;
    }

    public Vector3f getInitialRotation() {
        return this.initialRotation;
    }

    public Vector3f getInitialPosition() {
        return this.initialPosition;
    }

    public Vector3f getInitialScale() {
        return this.initialScale;
    }

    public float getPivotX() {
        return this.definition.getPivotX();
    }

    public float getPivotY() {
        return this.definition.getPivotY();
    }

    public float getPivotZ() {
        return this.definition.getPivotZ();
    }

    public float getRotationX() {
        return this.matrixData[this.matrixOffset];
    }

    public float getRotationY() {
        return this.matrixData[this.matrixOffset + 1];
    }

    public float getRotationZ() {
        return this.matrixData[this.matrixOffset + 2];
    }

    public void setRotationX(float value) {
        this.matrixData[this.matrixOffset] = value;
    }

    public void setRotationY(float value) {
        this.matrixData[this.matrixOffset + 1] = value;
    }

    public void setRotationZ(float value) {
        this.matrixData[this.matrixOffset + 2] = value;
    }

    public void setRotation(float x, float y, float z) {
        this.matrixData[this.matrixOffset] = x;
        this.matrixData[this.matrixOffset + 1] = y;
        this.matrixData[this.matrixOffset + 2] = z;
    }

    public float getPositionX() {
        return this.matrixData[this.matrixOffset + 3];
    }

    public float getPositionY() {
        return this.matrixData[this.matrixOffset + 4];
    }

    public float getPositionZ() {
        return this.matrixData[this.matrixOffset + 5];
    }

    public void setPositionX(float value) {
        this.matrixData[this.matrixOffset + 3] = value;
    }

    public void setPositionY(float value) {
        this.matrixData[this.matrixOffset + 4] = value;
    }

    public void setPositionZ(float value) {
        this.matrixData[this.matrixOffset + 5] = value;
    }

    public void setPosition(float x, float y, float z) {
        this.matrixData[this.matrixOffset + 3] = x;
        this.matrixData[this.matrixOffset + 4] = y;
        this.matrixData[this.matrixOffset + 5] = z;
    }

    public float getScaleX() {
        return this.matrixData[this.matrixOffset + 6];
    }

    public float getScaleY() {
        return this.matrixData[this.matrixOffset + 7];
    }

    public float getScaleZ() {
        return this.matrixData[this.matrixOffset + 8];
    }

    public void setScaleX(float value) {
        this.matrixData[this.matrixOffset + 6] = value;
    }

    public void setScaleY(float value) {
        this.matrixData[this.matrixOffset + 7] = value;
    }

    public void setScaleZ(float value) {
        this.matrixData[this.matrixOffset + 8] = value;
    }

    public void setScale(float x, float y, float z) {
        this.matrixData[this.matrixOffset + 6] = x;
        this.matrixData[this.matrixOffset + 7] = y;
        this.matrixData[this.matrixOffset + 8] = z;
    }

    public boolean isHidden() {
        return this.matrixData[this.matrixOffset + 9] != 0.0f;
    }

    public void setHidden(boolean hidden) {
        this.matrixData[this.matrixOffset + 9] = hidden ? 1.0f : 0.0f;
        this.matrixData[this.matrixOffset + 10] = hidden ? 1.0f : 0.0f;
    }

    public boolean cubesAreHidden() {
        return this.matrixData[this.matrixOffset + 9] != 0.0f;
    }

    public boolean childBonesAreHiddenToo() {
        return this.matrixData[this.matrixOffset + 10] != 0.0f;
    }

    public void setCubesHidden(boolean hidden) {
        this.matrixData[this.matrixOffset + 9] = hidden ? 1.0f : 0.0f;
    }

    public void setChildrenHidden(boolean hidden) {
        this.matrixData[this.matrixOffset + 10] = hidden ? 1.0f : 0.0f;
    }

    public void markTrackedLocator(boolean track) {
        this.matrixData[this.matrixOffset + 11] = track ? 1.0f : 0.0f;
    }

    public float getAbsPivotX() {
        return this.absPivotData[this.pivotOffset];
    }

    public float getAbsPivotY() {
        return this.absPivotData[this.pivotOffset + 1];
    }

    public float getAbsPivotZ() {
        return this.absPivotData[this.pivotOffset + 2];
    }
}
