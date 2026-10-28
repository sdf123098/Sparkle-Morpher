package com.micaftic.morpher.client.renderer.layer;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BowItemScaleTest {
    @Test void hudProjectionMustNotShrinkBowToOnePixel() {
        Matrix4f pose = new Matrix4f().scaling(40, -40, 40).rotateXYZ(.2f, .7f, -.1f);
        pose.scale(BowItemScale.correction(pose, 40));
        assertEquals(40, BowItemScale.largestAxisScale(pose), 1.0e-4f);
    }

    @Test void removesOnlyAdditionalBoneEnlargementInHud() {
        Matrix4f pose = new Matrix4f().scaling(40).rotateY(.8f).scale(3);
        pose.scale(BowItemScale.correction(pose, 40));
        assertEquals(40, BowItemScale.largestAxisScale(pose), 1.0e-4f);
    }

    @Test void preservesSmallPlayersInheritedScale() {
        Matrix4f pose = new Matrix4f().scaling(.7f).scale(3);
        pose.scale(BowItemScale.correction(pose, .7f));
        assertEquals(.7f, BowItemScale.largestAxisScale(pose), 1.0e-5f);
    }

    @Test void stillNormalizesExcessBoneScaleInWorld() {
        Matrix4f pose = new Matrix4f().scaling(3);
        pose.scale(BowItemScale.correction(pose, 1));
        assertEquals(1, BowItemScale.largestAxisScale(pose), 1.0e-5f);
    }

    @Test void neverResurrectsOrEnlargesHiddenOrAuthoredSmallItems() {
        for (float scale : new float[]{0, 1.0e-6f, .4f, 20}) {
            Matrix4f pose = new Matrix4f().scaling(scale);
            assertEquals(1, BowItemScale.correction(pose, 40));
        }
    }

    @Test void invalidReferenceCannotInjectNonFiniteTransform() {
        for (float inherited : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertEquals(1, BowItemScale.correction(new Matrix4f().scaling(40), inherited));
        }
        assertEquals(1, BowItemScale.correction(new Matrix4f().scaling(Float.NaN), 40));
    }
}
