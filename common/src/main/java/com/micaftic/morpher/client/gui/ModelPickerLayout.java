package com.micaftic.morpher.client.gui;

/**
 * 模型选择页的网格形态判定与卡片排版（纯数学，不引用任何 Minecraft 状态，便于纯 JVM 单测）。
 *
 * <p>为什么单独成类：贡献者版本把「卡片页号」和「文字网格行号」共用同一个
 * {@code STATE.modelScroll}，判定公式与消费公式分散在多处（预载、滚轮、翻页各算一遍），
 * 于是出现页数错位与预载越界。这里把形态判定与排版收敛成单一来源——
 * 调用方先 {@link #resolve} 拿到形态，再按该形态取对应 metrics，两者不会混用。</p>
 *
 * <p>关于 GUI 缩放：判定只看列表区<b>逻辑</b>尺寸。{@code guiScale} 是物理/逻辑比值，
 * 与可用逻辑空间无关，把它当「空间够不够」的代理会让同一台显示器换个缩放就突变布局。
 * 高缩放下用户想要卡片时，用 {@link Style#CARDS} 显式覆盖（放宽到 1×1 并改滚动）。</p>
 */
final class ModelPickerLayout {

    /** 用户可见的展示偏好（持久化到配置，并可在模型页当场切换）。 */
    enum Style {
        /** 列表区面积够就卡片、不够自动回退文字网格（默认）。 */
        AUTO,
        /** 强制卡片：放宽最小尺寸并改用滚动，仅在连一张最小可读卡片都放不下时回退。 */
        CARDS,
        /** 强制经典图标+文字网格。 */
        LIST
    }

    /** 当前实际生效的网格形态。 */
    enum GridMode {
        TEXT_ROWS,
        CARDS
    }

    /** 卡片目标宽（参与列数推导）。 */
    static final int CARD_TARGET_W = 116;
    /** 卡片最小宽（保证名字可读）。 */
    static final int CARD_MIN_W = 64;
    /** 卡片最大宽（避免大屏上单卡过大）。 */
    static final int CARD_MAX_W = 168;
    /**
     * 竖卡比例（高 = 宽 × 该值）。
     *
     * <p>{@code 90f / 52f}：YSM 卡面素材的标准尺寸就是 <b>52×90</b>（如 {@code gui_background}
     * 的「背景图.png」与 {@code gui_foreground} 的「前景图.png」边框，内置 default 模型也是这一对）。
     * 卡比例与素材一致，卡图才能整幅铺满、不出现两侧留白。</p>
     */
    static final float CARD_ASPECT = 90.0f / 52.0f;
    /** 名字条高度 = 卡高 × 该比例，并 clamp 到下面上下限。 */
    static final float CARD_NAME_RATIO = 0.24f;
    static final int CARD_NAME_MIN = 16;
    static final int CARD_NAME_MAX = 34;
    /** 卡间距与列表区四周留白。 */
    static final int CARD_GAP = 4;
    static final int CARD_MARGIN = 5;
    /**
     * 卡片内实时小人缩放 = 封面高 × 该系数。
     *
     * <p>取值来自右侧详情栏长期验证过的比例（原 {@code Math.min(54, h * 0.43f)}）：
     * 系数偏大会让模型超出卡片上沿被裁掉（表现为「头部被遮挡」），偏小则填不满卡面。</p>
     */
    static final float CARD_FIGURE_SCALE = 0.43f;
    /** 小人缩放的下限（像素）。仅作为偏好下限，实际还会被封面高夹住。 */
    static final int CARD_FIGURE_MIN = 16;
    static final int CARD_FIGURE_MAX = 64;

    /**
     * 卡片内小人缩放（像素）。恒 ≤ 封面高：否则模型会超出卡面上沿被裁（头部被遮挡）。
     */
    static int figureScale(int coverH) {
        int preferred = clamp(Math.round(coverH * CARD_FIGURE_SCALE), CARD_FIGURE_MIN, CARD_FIGURE_MAX);
        return Math.max(1, Math.min(preferred, coverH));
    }
    /** AUTO 判定：进入卡片模式所需的最小列表区尺寸。 */
    static final int CARDS_MIN_W = 150;
    static final int CARDS_MIN_H = 130;
    /** CARDS 强制的硬下限：低于此值卡片不可读，只能回退文字网格。 */
    static final int FORCED_MIN_CARD_W = 40;
    static final int FORCED_MIN_CARD_H = 48;
    /**
     * 每页最多列/行。行列上限与 {@link #cards} 的面积搜索共同决定每页容量，
     * 也就是「每帧实时 3D 预览数」的硬顶；取 8×6 是为了让高瘦面板也能把纵向空间排满，
     * 实际典型页面远小于此值。
     */
    static final int MAX_COLS = 8;
    static final int MAX_ROWS = 6;

    private ModelPickerLayout() {
    }

    /**
     * 形态判定。{@code style} 为 {@link Style#CARDS} 时只受硬下限约束，
     * 因此高 GUI 缩放的窄区域也能拿到卡片；{@link Style#AUTO} 才做面积自适应。
     */
    static GridMode resolve(Style style, int listW, int listH) {
        if (style == Style.LIST) {
            return GridMode.TEXT_ROWS;
        }
        if (style == Style.CARDS) {
            return forcedCardFits(listW, listH) ? GridMode.CARDS : GridMode.TEXT_ROWS;
        }
        return listW >= CARDS_MIN_W && listH >= CARDS_MIN_H ? GridMode.CARDS : GridMode.TEXT_ROWS;
    }

    /** 强制卡片模式下是否仍有最小可读空间。 */
    static boolean forcedCardFits(int listW, int listH) {
        Cards m = cards(listW, listH);
        return m.cellW() >= FORCED_MIN_CARD_W && m.cellH() >= FORCED_MIN_CARD_H;
    }

    /**
     * 卡片排版：在可用面积里选出最合适的列×行组合。
     *
     * <p>卡宽固定按 52:90 比例（与卡面素材一致），所以选定列数后行数由高度定死。
     * 遍历列数（1..{@link #MAX_COLS}），**优先取每页张数最多**的排法（既不浪费空间、
     * 也不至于只摆两张巨大卡），张数相同时取卡更大者，避免把卡压得过小。</p>
     */
    static Cards cards(int listW, int listH) {
        int usableW = Math.max(1, listW - 2 * CARD_MARGIN);
        int usableH = Math.max(1, listH - 2 * CARD_MARGIN);
        int bestCols = 0;
        int bestRows = 0;
        int bestW = 0;
        int bestH = 0;
        int bestCapacity = 0;
        for (int cols = 1; cols <= MAX_COLS; cols++) {
            int wFit = (usableW - (cols - 1) * CARD_GAP) / cols;
            if (wFit < CARD_MIN_W) {
                break; // 再往后的列数只会更窄
            }
            int cellW = Math.min(wFit, CARD_MAX_W);
            int cellH = cardH(cellW);
            int rows = (usableH + CARD_GAP) / (cellH + CARD_GAP);
            if (rows < 1) {
                continue;
            }
            rows = Math.min(rows, MAX_ROWS);
            int capacity = cols * rows;
            boolean better = capacity > bestCapacity
                    || (capacity == bestCapacity && cellW > bestW);
            if (better) {
                bestCapacity = capacity;
                bestCols = cols;
                bestRows = rows;
                bestW = cellW;
                bestH = cellH;
            }
        }
        if (bestCols == 0) {
            // 放不下任何最小可读卡：给出单张「宽高都放得下」的卡，
            // 由调用方决定是否回退文字网格（此处不得超出可用区，否则排版会溢出）。
            int byWidth = Math.min(usableW, CARD_MAX_W);
            int byHeight = (int) Math.floor(usableH / CARD_ASPECT);
            int cellW = Math.max(1, Math.min(byWidth, byHeight));
            return new Cards(1, 1, cellW, Math.max(1, Math.min(cardH(cellW), usableH)), 1);
        }
        return new Cards(bestCols, bestRows, bestW, bestH, bestCapacity);
    }

    /** 卡高（含名字条）。 */
    static int cardH(int cellW) {
        return Math.max(1, Math.round(cellW * CARD_ASPECT));
    }

    /**
     * 名字条高度（压在卡底，保证不被 3D 小人遮挡）。
     *
     * <p>始终给封面留出 ≥1px：卡极矮时名字条跟着缩，而不是让固定下限反超卡高
     * （否则 {@link #coverH} 会算成负值）。</p>
     */
    static int nameBandH(int cellH) {
        if (cellH <= 1) {
            return Math.max(1, cellH);
        }
        int band = clamp(Math.round(cellH * CARD_NAME_RATIO), CARD_NAME_MIN, CARD_NAME_MAX);
        return Math.min(band, cellH - 1);
    }

    /** 卡片绘图区高度（卡高减名字条，恒 ≥1）。 */
    static int coverH(int cellH) {
        return Math.max(1, cellH - nameBandH(cellH));
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 卡片排版结果。{@code capacity} 为每页容量，翻页步长即 {@code rows}。
     */
    record Cards(int cols, int rows, int cellW, int cellH, int capacity) {

        int blockW() {
            return cols * cellW + (cols - 1) * CARD_GAP;
        }

        int blockH() {
            return rows * cellH + (rows - 1) * CARD_GAP;
        }

        int totalPages(int entryCount) {
            return entryCount <= 0 ? 1 : (entryCount + capacity - 1) / capacity;
        }

        /** 整块居中后，相对列表区左上角的 X 偏移（至少留出边距）。 */
        int originX(int listW) {
            return Math.max(CARD_MARGIN, (listW - blockW()) / 2);
        }

        /** 整块居中后，相对列表区左上角的 Y 偏移（至少留出边距）。 */
        int originY(int listH) {
            return Math.max(CARD_MARGIN, (listH - blockH()) / 2);
        }
    }
}
