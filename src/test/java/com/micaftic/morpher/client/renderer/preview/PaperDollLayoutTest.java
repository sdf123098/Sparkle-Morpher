package com.micaftic.morpher.client.renderer.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 1.2.6 §22.2 anchor 契约：锚点解析与反解析（MC-free）。
 *
 * <p>关键回归：{@code TOP_LEFT} + 偏移必须与历史「绝对像素位置」完全等价。</p>
 */
class PaperDollLayoutTest {

    private static final int SW = 1920;
    private static final int SH = 1080;
    private static final int W = 40;
    private static final int H = 80;

    @Test
    void topLeftMatchesLegacyAbsolutePosition() {
        PaperDollLayout.Position p = PaperDollLayout.resolve(PaperDollLayout.Anchor.TOP_LEFT, SW, SH, 10, 10, W, H);
        assertEquals(10, p.x());
        assertEquals(10, p.y());
    }

    @Test
    void nullAnchorFallsBackToTopLeft() {
        PaperDollLayout.Position p = PaperDollLayout.resolve(null, SW, SH, 7, 9, W, H);
        assertEquals(7, p.x());
        assertEquals(9, p.y());
    }

    @Test
    void bottomRightIsInsetFromEdges() {
        PaperDollLayout.Position p = PaperDollLayout.resolve(PaperDollLayout.Anchor.BOTTOM_RIGHT, SW, SH, 10, 20, W, H);
        assertEquals(SW - W - 10, p.x());
        assertEquals(SH - H - 20, p.y());
    }

    @Test
    void topRightUsesRightEdgeOffset() {
        PaperDollLayout.Position p = PaperDollLayout.resolve(PaperDollLayout.Anchor.TOP_RIGHT, SW, SH, 5, 3, W, H);
        assertEquals(SW - W - 5, p.x());
        assertEquals(3, p.y());
    }

    @Test
    void centerIsCenteredOnScreen() {
        PaperDollLayout.Position p = PaperDollLayout.resolve(PaperDollLayout.Anchor.MIDDLE_CENTER, SW, SH, 0, 0, W, H);
        assertEquals((SW - W) / 2, p.x());
        assertEquals((SH - H) / 2, p.y());
    }

    @Test
    void toOffsetIsInverseOfResolveForEveryAnchor() {
        for (PaperDollLayout.Anchor anchor : PaperDollLayout.Anchor.values()) {
            PaperDollLayout.Position pos = PaperDollLayout.resolve(anchor, SW, SH, 12, 34, W, H);
            PaperDollLayout.Position back = PaperDollLayout.toOffset(anchor, SW, SH, pos.x(), pos.y(), W, H);
            assertEquals(12, back.x(), "x offset round-trip failed for " + anchor);
            assertEquals(34, back.y(), "y offset round-trip failed for " + anchor);
        }
    }
}
