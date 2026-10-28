package com.micaftic.morpher.geckolib3.core.util;

import org.joml.Vector3f;

public class TransitionVector3f extends Vector3f {

    public enum RotationBlendMode {
        LEGACY_QUATERNION,
        EULER,
        SHORTEST_EULER
    }

    public float percentCompleted;

    /** Native controllers retain quaternion blending; authored states select their Euler policy. */
    public RotationBlendMode rotationBlendMode = RotationBlendMode.LEGACY_QUATERNION;

    public TransitionVector3f() {
        this.percentCompleted = 1.0f;
    }

    public TransitionVector3f(float x, float y, float z) {
        super(x, y, z);
        this.percentCompleted = 1.0f;
    }

    public TransitionVector3f(Vector3f v) {
        super(v);
        this.percentCompleted = 1.0f;
    }

    public void setPercentCompleted(float newPercent) {
        if (newPercent < this.percentCompleted) {
            this.percentCompleted = newPercent;
        }
    }

    public void applyLinearBlendTo(Vector3f targetVec) {
        float progress = this.percentCompleted;
        if (progress == 0.0f) {
            targetVec.set(this);
        } else {
            MathUtil.lerpValues(progress, this, targetVec, targetVec);
        }
    }

    public void applyRotationBlendTo(Vector3f targetEuler, Vector3f offsetEuler) {
        float progress = this.percentCompleted;
        if (progress == 0.0f) {
            targetEuler.set(this);
        } else {
            blendRotation(progress, this, targetEuler, offsetEuler, targetEuler, null);
        }
    }

    public void applyRotationBlendTo(Vector3f targetEuler, Vector3f offsetEuler, EulerNlerpScratch scratch) {
        float progress = this.percentCompleted;
        if (progress == 0.0f) {
            targetEuler.set(this);
        } else {
            blendRotation(progress, this, targetEuler, offsetEuler, targetEuler, scratch);
        }
    }

    public void blendRotation(float progress, Vector3f startEuler, Vector3f endEuler,
                              Vector3f offsetEuler, Vector3f outEuler, EulerNlerpScratch scratch) {
        if (this.rotationBlendMode == RotationBlendMode.EULER) {
            MathUtil.lerpValues(progress, startEuler, endEuler, outEuler);
        } else if (this.rotationBlendMode == RotationBlendMode.SHORTEST_EULER) {
            outEuler.set(startEuler.x + progress * MathUtil.normalizeAngle(endEuler.x - startEuler.x),
                    startEuler.y + progress * MathUtil.normalizeAngle(endEuler.y - startEuler.y),
                    startEuler.z + progress * MathUtil.normalizeAngle(endEuler.z - startEuler.z));
        } else if (scratch == null) {
            MathUtil.nlerpEulerAngles(progress, startEuler, endEuler, offsetEuler, outEuler);
        } else {
            MathUtil.nlerpEulerAngles(progress, startEuler, endEuler, offsetEuler, outEuler, scratch);
        }
    }
}
