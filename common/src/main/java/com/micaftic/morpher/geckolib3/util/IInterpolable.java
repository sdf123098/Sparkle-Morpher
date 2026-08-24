package com.micaftic.morpher.geckolib3.util;

public interface IInterpolable {
    float interpolate(float f);

    float getProgress();

    default IInterpolable asInterpolator() {
        return this;
    }
}