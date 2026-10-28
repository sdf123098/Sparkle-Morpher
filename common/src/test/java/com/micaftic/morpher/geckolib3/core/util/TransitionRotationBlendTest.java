package com.micaftic.morpher.geckolib3.core.util;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransitionRotationBlendTest {
    @Test
    void declaredOrdinaryBlendPreservesTheAuthoredLongRotation() {
        TransitionVector3f source = new TransitionVector3f(radians(350), 0, 0);
        source.percentCompleted = 0.5f;
        source.rotationBlendMode = TransitionVector3f.RotationBlendMode.EULER;
        Vector3f target = new Vector3f(radians(10), 0, 0);

        source.applyRotationBlendTo(target, new Vector3f(), new EulerNlerpScratch());

        assertEquals(radians(180), target.x, 1.0E-5f);
    }

    @Test
    void declaredShortestBlendCrossesTheWrapBoundary() {
        TransitionVector3f source = new TransitionVector3f(radians(350), 0, 0);
        source.percentCompleted = 0.5f;
        source.rotationBlendMode = TransitionVector3f.RotationBlendMode.SHORTEST_EULER;
        Vector3f target = new Vector3f(radians(10), 0, 0);

        source.applyRotationBlendTo(target, new Vector3f(), new EulerNlerpScratch());

        assertEquals(radians(360), target.x, 1.0E-5f);
    }

    @Test
    void nativeControllersRetainTheirQuaternionDefault() {
        TransitionVector3f source = new TransitionVector3f(radians(350), 0, 0);
        source.percentCompleted = 0.5f;
        Vector3f target = new Vector3f(radians(10), 0, 0);

        source.applyRotationBlendTo(target, new Vector3f());

        assertEquals(TransitionVector3f.RotationBlendMode.LEGACY_QUATERNION, source.rotationBlendMode);
        assertEquals(0, Math.sin(target.x), 1.0E-5);
        assertEquals(1, Math.cos(target.x), 1.0E-5);
    }

    @Test
    void ordinaryBeginningBlendInterpolatesEachAuthoredEulerAxis() {
        TransitionVector3f output = new TransitionVector3f();
        output.rotationBlendMode = TransitionVector3f.RotationBlendMode.EULER;
        Vector3f start = new Vector3f(radians(170), radians(-170), radians(350));
        Vector3f end = new Vector3f(radians(-170), radians(170), radians(10));

        output.blendRotation(0.5f, start, end, new Vector3f(0.3f, 0.2f, 0.1f), end, new EulerNlerpScratch());

        assertEquals(0, end.x, 1.0E-5f);
        assertEquals(0, end.y, 1.0E-5f);
        assertEquals(radians(180), end.z, 1.0E-5f);
    }

    @Test
    void shortestBeginningBlendKeepsEachEulerAxisOnItsShortPath() {
        TransitionVector3f output = new TransitionVector3f();
        output.rotationBlendMode = TransitionVector3f.RotationBlendMode.SHORTEST_EULER;
        Vector3f start = new Vector3f(radians(170), radians(-170), radians(350));
        Vector3f end = new Vector3f(radians(-170), radians(170), radians(10));

        output.blendRotation(0.5f, start, end, new Vector3f(0.3f, 0.2f, 0.1f), end, new EulerNlerpScratch());

        assertEquals(radians(180), end.x, 1.0E-5f);
        assertEquals(radians(-180), end.y, 1.0E-5f);
        assertEquals(radians(360), end.z, 1.0E-5f);
    }

    @Test
    void overloadWithoutScratchHonorsTheAuthoredEulerPolicy() {
        TransitionVector3f source = new TransitionVector3f(radians(350), 0, 0);
        source.percentCompleted = 0.5f;
        source.rotationBlendMode = TransitionVector3f.RotationBlendMode.EULER;
        Vector3f target = new Vector3f(radians(10), 0, 0);

        source.applyRotationBlendTo(target, new Vector3f());

        assertEquals(radians(180), target.x, 1.0E-5f);
    }

    @Test
    void zeroProgressKeepsTheExactSourceWinding() {
        TransitionVector3f source = new TransitionVector3f(radians(710), radians(-370), 0);
        source.percentCompleted = 0;
        source.rotationBlendMode = TransitionVector3f.RotationBlendMode.SHORTEST_EULER;
        Vector3f target = new Vector3f(radians(10), radians(-10), 0);

        source.applyRotationBlendTo(target, new Vector3f(), new EulerNlerpScratch());

        assertEquals(source.x, target.x);
        assertEquals(source.y, target.y);
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

}
