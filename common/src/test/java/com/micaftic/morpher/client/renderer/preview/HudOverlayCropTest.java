package com.micaftic.morpher.client.renderer.preview;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HudOverlayCropTest {
    @Test void asymmetricModelCropKeepsTheFeetAtTheLayoutBottomCenter() {
        var origin = HudOverlayCrop.compositeOrigin(72, 56, 50, 2, 6, 2);
        assertEquals(97, origin.x() + 2 + 2 * 50, 0.0001);
        assertEquals(156, origin.y() + 2 + 6 * 50, 0.0001);
    }

    @Test void cropChangesDoNotMoveTheConfiguredAnchorAtAnyGuiScale() {
        for (float scale : new float[] {8, 40, 50, 137.5f, 360}) {
            for (float maxX : new float[] {0.5f, 2, 4.25f}) {
                for (float maxY : new float[] {1.8f, 2, 6}) {
                    var origin = HudOverlayCrop.compositeOrigin(19, 31, scale, maxX, maxY, 2);
                    for (double guiScale : new double[] {1, 2, 3, 4}) {
                        assertEquals((19 + scale / 2) * guiScale,
                                (origin.x() + 2 + maxX * scale) * guiScale, 0.001);
                        assertEquals((31 + scale * 2) * guiScale,
                                (origin.y() + 2 + maxY * scale) * guiScale, 0.001);
                    }
                }
            }
        }
    }

    @Test void standardOneByTwoModelPreservesExistingPlacement() {
        var origin = HudOverlayCrop.compositeOrigin(10, 20, 40, 0.5f, 2, 2);
        assertEquals(8, origin.x());
        assertEquals(18, origin.y());
    }
}
