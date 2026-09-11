package com.micaftic.morpher.client.renderer.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.2.6 Slice A：预览投影/鼠标旋转纯数学契约（MC-free）。
 *
 * <p>{@link PreviewMath} 从 {@code ModelPreviewRenderer} 外提，保证拆分不改变
 * 预览交互（鼠标跟随、死区、偏移换算、yaw 反算）的行为。</p>
 */
class PreviewMathTest {

    @Test
    void disabledRotationReturnsNoRotation() {
        assertSame(PreviewMath.NO_MOUSE_ROTATION,
                PreviewMath.mouseRotation(0, 0, 100, 200, 10, 10, true));
    }

    @Test
    void degenerateBoundsReturnNoRotation() {
        assertSame(PreviewMath.NO_MOUSE_ROTATION,
                PreviewMath.mouseRotation(50, 0, 50, 200, 10, 10, false));
        assertSame(PreviewMath.NO_MOUSE_ROTATION,
                PreviewMath.mouseRotation(0, 50, 100, 50, 10, 10, false));
    }

    @Test
    void unknownMouseReturnsNoRotation() {
        assertSame(PreviewMath.NO_MOUSE_ROTATION,
                PreviewMath.mouseRotation(0, 0, 100, 200, Integer.MIN_VALUE, Integer.MIN_VALUE, false));
    }

    @Test
    void mouseAtCenterProducesNoOffset() {
        PreviewMath.MouseRotation rotation = PreviewMath.mouseRotation(0, 0, 100, 200, 50, 100, false);
        assertEquals(0.0f, rotation.yaw(), 1.0e-6f);
        assertEquals(0.0f, rotation.pitch(), 1.0e-6f);
    }

    @Test
    void mouseAtLeftEdgeProducesMaxPositiveYaw() {
        PreviewMath.MouseRotation rotation = PreviewMath.mouseRotation(0, 0, 100, 200, 0, 100, false);
        assertEquals(PreviewMath.MODEL_PREVIEW_MOUSE_YAW_DEGREES, rotation.yaw(), 1.0e-6f);
        assertEquals(0.0f, rotation.pitch(), 1.0e-6f);
    }

    @Test
    void offsetIsClampedToOneNormalizedUnit() {
        // Mouse far outside the box must clamp to the configured maximum.
        PreviewMath.MouseRotation rotation = PreviewMath.mouseRotation(0, 0, 100, 200, -10_000, 100, false);
        assertEquals(PreviewMath.MODEL_PREVIEW_MOUSE_YAW_DEGREES, rotation.yaw(), 1.0e-6f);
    }

    @Test
    void deadzoneZeroesSmallValuesAndKeepsLargeOnes() {
        assertEquals(0.0f, PreviewMath.applyDeadzone(0.05f), 1.0e-6f);
        assertEquals(0.0f, PreviewMath.applyDeadzone(-0.05f), 1.0e-6f);
        assertEquals(0.5f, PreviewMath.applyDeadzone(0.5f), 1.0e-6f);
        assertEquals(-0.5f, PreviewMath.applyDeadzone(-0.5f), 1.0e-6f);
    }

    @Test
    void toModelOffsetIsZeroAtBoxCenter() {
        assertEquals(0.0f, PreviewMath.toModelOffset(50.0f, 0, 100, 40.0f), 1.0e-6f);
    }

    @Test
    void toModelOffsetGuardsNonPositiveScale() {
        // scale <= 0 falls back to the max(1, scale) guard rather than dividing by zero.
        assertEquals(40.0f, PreviewMath.toModelOffset(90.0f, 0, 100, 0.0f), 1.0e-6f);
        assertEquals(40.0f, PreviewMath.toModelOffset(90.0f, 0, 100, -3.0f), 1.0e-6f);
    }

    @Test
    void toModelOffsetScalesByDivisor() {
        assertEquals(1.0f, PreviewMath.toModelOffset(90.0f, 0, 100, 40.0f), 1.0e-6f);
    }

    @Test
    void mouseXFromYawInverts180ToOrigin() {
        assertEquals(100, PreviewMath.mouseXFromYaw(100.0f, 180.0f));
    }

    @Test
    void mouseXFromYawIsSymmetricAroundOriginForTiltedYaw() {
        // +10 and -10 deg yaw about 180 must land symmetrically around the origin X.
        boolean symmetric = PreviewMath.mouseXFromYaw(100.0f, 190.0f) < 100
                && PreviewMath.mouseXFromYaw(100.0f, 170.0f) > 100;
        assertTrue(symmetric, "yaw tilt must move the implied mouse X monotonically");
    }
}
