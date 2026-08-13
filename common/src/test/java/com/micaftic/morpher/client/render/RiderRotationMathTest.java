package com.micaftic.morpher.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiderRotationMathTest {

    @Test
    void reconstructsAbsoluteHeadYawFromRenderState() {
        assertEquals(140.0f, RiderRotationMath.absoluteHeadYaw(120.0f, 20.0f));
        assertEquals(200.0f, RiderRotationMath.absoluteHeadYaw(170.0f, 30.0f));
    }

    @Test
    void keepsSmallHeadDeltaRelativeToLivingVehicle() {
        RiderRotationMath.LivingVehicleRotation rotation =
                RiderRotationMath.constrainToLivingVehicle(140.0f, 120.0f);

        assertEquals(120.0f, rotation.bodyYaw());
        assertEquals(20.0f, rotation.relativeHeadYaw());
    }

    @Test
    void clampsAndSoftensLargeHeadDeltaLikeVanilla() {
        RiderRotationMath.LivingVehicleRotation rotation =
                RiderRotationMath.constrainToLivingVehicle(100.0f, 0.0f);

        assertEquals(32.0f, rotation.bodyYaw());
        assertEquals(68.0f, rotation.relativeHeadYaw());
    }

    @Test
    void handlesEquivalentAnglesAcrossWrapBoundary() {
        RiderRotationMath.LivingVehicleRotation rotation =
                RiderRotationMath.constrainToLivingVehicle(200.0f, -160.0f);

        assertEquals(200.0f, rotation.bodyYaw());
        assertEquals(0.0f, rotation.relativeHeadYaw());
    }
}
