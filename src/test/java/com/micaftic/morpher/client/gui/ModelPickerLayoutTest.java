package com.micaftic.morpher.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型选择页网格形态判定与卡片排版的纯数学契约（MC-free）。
 *
 * <p>重点是两件容易做错的事：</p>
 * <ol>
 *   <li>高 GUI 缩放导致列表区逻辑尺寸偏小时，{@link ModelPickerLayout.Style#CARDS}
 *       仍必须给出卡片（这是「响应式合并 + 强制开关」方案的承诺）；</li>
 *   <li>排版结果必须自洽——容量等于列×行、整块不溢出列表区、卡宽落在上下限内。</li>
 * </ol>
 */
class ModelPickerLayoutTest {

    // ---- 形态判定 ----

    @Test
    void listStyleAlwaysUsesTextRowsEvenWhenHuge() {
        assertEquals(ModelPickerLayout.GridMode.TEXT_ROWS,
                ModelPickerLayout.resolve(ModelPickerLayout.Style.LIST, 4000, 3000));
        assertEquals(ModelPickerLayout.GridMode.TEXT_ROWS,
                ModelPickerLayout.resolve(ModelPickerLayout.Style.LIST, 10, 10));
    }

    @Test
    void autoStyleUsesCardsWhenAreaIsSufficient() {
        assertEquals(ModelPickerLayout.GridMode.CARDS,
                ModelPickerLayout.resolve(ModelPickerLayout.Style.AUTO,
                        ModelPickerLayout.CARDS_MIN_W, ModelPickerLayout.CARDS_MIN_H));
        assertEquals(ModelPickerLayout.GridMode.CARDS,
                ModelPickerLayout.resolve(ModelPickerLayout.Style.AUTO, 600, 400));
    }

    @Test
    void autoStyleFallsBackToTextRowsWhenAreaIsTooSmall() {
        // 宽度不足
        assertEquals(ModelPickerLayout.GridMode.TEXT_ROWS,
                ModelPickerLayout.resolve(ModelPickerLayout.Style.AUTO,
                        ModelPickerLayout.CARDS_MIN_W - 1, 400));
        // 高度不足
        assertEquals(ModelPickerLayout.GridMode.TEXT_ROWS,
                ModelPickerLayout.resolve(ModelPickerLayout.Style.AUTO,
                        400, ModelPickerLayout.CARDS_MIN_H - 1));
    }

    @Test
    void forcedCardsOverridesAutoAreaDecision() {
        // 取一个 AUTO 会判为文字网格的尺寸，强制卡片仍应给出卡片。
        int w = ModelPickerLayout.CARDS_MIN_W - 40;
        int h = ModelPickerLayout.CARDS_MIN_H - 30;
        assertEquals(ModelPickerLayout.GridMode.TEXT_ROWS,
                ModelPickerLayout.resolve(ModelPickerLayout.Style.AUTO, w, h));
        assertEquals(ModelPickerLayout.GridMode.CARDS,
                ModelPickerLayout.resolve(ModelPickerLayout.Style.CARDS, w, h));
    }

    @Test
    void forcedCardsStillFallsBackWhenAbsolutelyNoRoom() {
        assertEquals(ModelPickerLayout.GridMode.TEXT_ROWS,
                ModelPickerLayout.resolve(ModelPickerLayout.Style.CARDS, 12, 12));
        assertEquals(ModelPickerLayout.GridMode.TEXT_ROWS,
                ModelPickerLayout.resolve(ModelPickerLayout.Style.CARDS, 0, 0));
    }

    @Test
    void forcedCardFitsRejectsUnreadablyTinyArea() {
        assertFalse(ModelPickerLayout.forcedCardFits(10, 10));
        assertTrue(ModelPickerLayout.forcedCardFits(
                ModelPickerLayout.CARDS_MIN_W, ModelPickerLayout.CARDS_MIN_H));
    }

    // ---- 卡片排版 ----

    @Test
    void cardsNeverReportFewerThanOneCellOrCapacity() {
        for (int w : new int[]{0, 1, 8, 40, 80, 200, 1000}) {
            for (int h : new int[]{0, 1, 8, 40, 80, 200, 1000}) {
                ModelPickerLayout.Cards m = ModelPickerLayout.cards(w, h);
                assertTrue(m.cols() >= 1, "cols>=1 for " + w + "x" + h);
                assertTrue(m.rows() >= 1, "rows>=1 for " + w + "x" + h);
                assertTrue(m.capacity() >= 1, "capacity>=1 for " + w + "x" + h);
                assertEquals(m.cols() * m.rows(), m.capacity(), "capacity==cols*rows for " + w + "x" + h);
            }
        }
    }

    @Test
    void cardWidthStaysWithinBounds() {
        for (int w : new int[]{60, 120, 200, 400, 800, 1600, 4000}) {
            ModelPickerLayout.Cards m = ModelPickerLayout.cards(w, 400);
            assertTrue(m.cellW() <= ModelPickerLayout.CARD_MAX_W, "cellW<=max for " + w);
            assertTrue(m.cellW() >= 1, "cellW>=1 for " + w);
            assertEquals(ModelPickerLayout.cardH(m.cellW()), m.cellH(), "cellH matches aspect for " + w);
        }
    }

    @Test
    void cardBlockFitsInsideListAreaWhenThereIsRoom() {
        int w = 400;
        int h = 300;
        ModelPickerLayout.Cards m = ModelPickerLayout.cards(w, h);
        assertTrue(m.blockW() <= w, "block width must fit: " + m.blockW() + " <= " + w);
        assertTrue(m.blockH() <= h, "block height must fit: " + m.blockH() + " <= " + h);
    }

    @Test
    void blockIsCenteredWithAtLeastTheMargin() {
        int w = 400;
        int h = 300;
        ModelPickerLayout.Cards m = ModelPickerLayout.cards(w, h);
        assertTrue(m.originX(w) >= ModelPickerLayout.CARD_MARGIN);
        assertTrue(m.originY(h) >= ModelPickerLayout.CARD_MARGIN);
        // 居中后左右留白差不超过 1 像素（整数除法）。
        int left = m.originX(w);
        int right = w - (left + m.blockW());
        assertTrue(Math.abs(left - right) <= 1, "block should be centred");
    }

    @Test
    void largerAreaNeverYieldsFewerCells() {
        ModelPickerLayout.Cards small = ModelPickerLayout.cards(300, 250);
        ModelPickerLayout.Cards large = ModelPickerLayout.cards(700, 500);
        assertTrue(large.capacity() >= small.capacity(), "more area must not reduce capacity");
    }

    @Test
    void perPageCapacityIsBounded() {
        // 每张可见卡都渲染实时 3D，故每页容量必须有硬顶（= 每帧预览数上限）。
        int cap = ModelPickerLayout.MAX_COLS * ModelPickerLayout.MAX_ROWS;
        for (int w : new int[]{300, 600, 1200, 4000}) {
            for (int h : new int[]{200, 400, 800, 2000}) {
                ModelPickerLayout.Cards m = ModelPickerLayout.cards(w, h);
                assertTrue(m.capacity() <= cap,
                        "capacity " + m.capacity() + " must not exceed " + cap + " at " + w + "x" + h);
            }
        }
    }

    @Test
    void totalPagesRoundsUpAndHandlesEmpty() {
        ModelPickerLayout.Cards m = ModelPickerLayout.cards(400, 300);
        assertEquals(1, m.totalPages(0), "empty list still has one page");
        assertEquals(1, m.totalPages(m.capacity()), "exactly one page of entries");
        assertEquals(2, m.totalPages(m.capacity() + 1), "one entry over spills to page 2");
        assertEquals(3, m.totalPages(m.capacity() * 2 + 1));
    }

    // ---- 名字条 / 封面 ----

    @Test
    void nameBandStaysWithinClamps() {
        // 卡高足够时命中最大值与比例值；卡高不足时让位给封面（下限不反超卡高）。
        assertEquals(ModelPickerLayout.CARD_NAME_MAX, ModelPickerLayout.nameBandH(100_000));
        assertEquals(ModelPickerLayout.CARD_NAME_MAX, ModelPickerLayout.nameBandH(500));
        assertEquals(Math.round(80 * ModelPickerLayout.CARD_NAME_RATIO), ModelPickerLayout.nameBandH(80));
        for (int cellH : new int[]{20, 48, 100, 120, 160, 220, 400}) {
            int band = ModelPickerLayout.nameBandH(cellH);
            assertTrue(band >= 1, "band>=1 for cellH=" + cellH);
            assertTrue(band <= ModelPickerLayout.CARD_NAME_MAX, "band<=max for cellH=" + cellH);
            assertTrue(band < cellH, "band must leave room for cover at cellH=" + cellH);
        }
    }

    @Test
    void coverHeightLeavesRoomForTheNameBand() {
        for (int cellH : new int[]{0, 1, 2, 5, 17, 20, 48, 100, 120, 400}) {
            int cover = ModelPickerLayout.coverH(cellH);
            assertTrue(cover >= 1, "cover>=1 for cellH=" + cellH);
        }
    }

    @Test
    void nameBandNeverConsumesTheWholeCard() {
        // 回归：卡极矮时名字条曾因固定下限反超卡高，导致封面高度为负。
        for (int cellH = 0; cellH <= 260; cellH++) {
            int band = ModelPickerLayout.nameBandH(cellH);
            int cover = ModelPickerLayout.coverH(cellH);
            assertTrue(band >= 1, "band>=1 for cellH=" + cellH);
            assertTrue(cover >= 1, "cover>=1 for cellH=" + cellH);
            if (cellH >= 2) {
                assertEquals(cellH, band + cover, "band+cover==cellH for cellH=" + cellH);
            }
        }
    }

    @Test
    void cardAspectMatchesTheStandardCardArt() {
        // 回归：YSM 卡面素材标准尺寸是 52x90（gui_background/gui_foreground，内置 default 亦然）。
        // 卡比例必须与素材一致，否则卡图两侧会出现留白（空间利用率不足）。
        assertEquals(90.0f / 52.0f, ModelPickerLayout.CARD_ASPECT, 1.0e-6f);
        for (int w : new int[]{64, 80, 116, 168}) {
            int h = ModelPickerLayout.cardH(w);
            double aspect = (double) h / w;
            assertTrue(Math.abs(aspect - 90.0 / 52.0) < 0.02, "card aspect must match 52:90 at w=" + w);
        }
    }

    @Test
    void figureScaleFitsInsideTheCover() {
        // 回归：小人缩放偏大会让模型超出卡片上沿被裁（头部被遮挡）。
        // 与右侧详情栏同比例（coverH * 0.43），且恒 <= 封面高。
        assertTrue(ModelPickerLayout.CARD_FIGURE_SCALE > 0f && ModelPickerLayout.CARD_FIGURE_SCALE <= 0.5f,
                "figure scale must be conservative enough to fit");
        for (int ch = 1; ch <= 400; ch++) {
            int cover = ModelPickerLayout.coverH(ch);
            int fig = ModelPickerLayout.figureScale(cover);
            assertTrue(fig >= 1, "fig>=1 for ch=" + ch);
            assertTrue(fig <= cover, "fig " + fig + " must fit cover " + cover + " at ch=" + ch);
        }
    }

    @Test
    void layoutFillsTheAvailableArea() {
        // 回归：原来只按最小卡宽推列数、并把行数压到 2，导致卡又小又只占一小块，
        // 上下左右留下大片空白。现在应尽量吃满可用面积。
        int[][] cases = {{592, 163}, {306, 294}, {347, 382}, {635, 187}, {284, 382}, {900, 420}};
        for (int[] c : cases) {
            int w = c[0], h = c[1];
            ModelPickerLayout.Cards m = ModelPickerLayout.cards(w, h);
            // 卡的横向铺开率：块宽应接近可用宽（或已到单卡宽度上限/列数上限）。
            int usableW = w - 2 * ModelPickerLayout.CARD_MARGIN;
            int usableH = h - 2 * ModelPickerLayout.CARD_MARGIN;
            boolean widthCappedByMaxCard = m.cols() >= ModelPickerLayout.MAX_COLS
                    || m.cellW() >= ModelPickerLayout.CARD_MAX_W;
            if (!widthCappedByMaxCard) {
                int slack = usableW - m.blockW();
                assertTrue(slack < m.cellW() + ModelPickerLayout.CARD_GAP,
                        "horizontal slack " + slack + " should be less than one card at " + w + "x" + h);
            }
            // 纵向同理：剩下的空档放不下再多一行。
            if (m.rows() < ModelPickerLayout.MAX_ROWS) {
                int slackV = usableH - m.blockH();
                assertTrue(slackV < m.cellH() + ModelPickerLayout.CARD_GAP,
                        "vertical slack " + slackV + " should be less than one row at " + w + "x" + h);
            }
        }
    }

    @Test
    void layoutInvariantsHoldAcrossSizeSweep() {
        for (int w = 1; w <= 900; w += 7) {
            for (int h = 1; h <= 520; h += 11) {
                ModelPickerLayout.Cards m = ModelPickerLayout.cards(w, h);
                assertTrue(m.cols() >= 1 && m.rows() >= 1, "cells>=1 for " + w + "x" + h);
                assertEquals(m.cols() * m.rows(), m.capacity(), "capacity for " + w + "x" + h);
                assertTrue(ModelPickerLayout.coverH(m.cellH()) >= 1, "cover>=1 for " + w + "x" + h);
                // 有足够空间时，整块不得溢出列表区。
                if (m.cellW() >= ModelPickerLayout.CARD_MIN_W) {
                    assertTrue(m.blockW() <= w, "blockW " + m.blockW() + " <= " + w);
                    assertTrue(m.blockH() <= h, "blockH " + m.blockH() + " <= " + h);
                }
            }
        }
    }
}
