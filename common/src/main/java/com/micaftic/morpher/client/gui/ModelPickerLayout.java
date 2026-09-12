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
    /** 竖卡比例（高 = 宽 × 该值）。 */
    static final float CARD_ASPECT = 1.62f;
    /** 名字条高度 = 卡高 × 该比例，并 clamp 到下面上下限。 */
    static final float CARD_NAME_RATIO = 0.24f;
    static final int CARD_NAME_MIN = 16;
    static final int CARD_NAME_MAX = 34;
    /** 卡间距与列表区四周留白。 */
    static final int CARD_GAP = 4;
    static final int CARD_MARGIN = 5;
    /** AUTO 判定：进入卡片模式所需的最小列表区尺寸。 */
    static final int CARDS_MIN_W = 150;
    static final int CARDS_MIN_H = 130;
    /** CARDS 强制的硬下限：低于此值卡片不可读，只能回退文字网格。 */
    static final int FORCED_MIN_CARD_W = 40;
    static final int FORCED_MIN_CARD_H = 48;
    /**
     * 每页最多列/行。行数上限同时是「每帧实时 3D 预览数」的硬顶——
     * 每张可见卡都渲染实时小人，COLS×ROWS ≤ 16 才能把开销压在合理区间
     * （典型页面为 8~10 张，与原 5×2 目录方案的密度一致）。
     */
    static final int MAX_COLS = 8;
    static final int MAX_ROWS = 2;

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
     * 卡片排版：列数由宽度推、行数由高度推，卡宽取宽高两方向都放得下的较小值，
     * 再用实际卡宽回收碎边（多出来的空间够放一列就再放一列）。
     */
    static Cards cards(int listW, int listH) {
        int usableW = Math.max(1, listW - 2 * CARD_MARGIN);
        int usableH = Math.max(1, listH - 2 * CARD_MARGIN);
        int cols = clamp((usableW + CARD_GAP) / (CARD_MIN_W + CARD_GAP), 1, MAX_COLS);
        int rows = clamp((usableH + CARD_GAP) / (cardH(CARD_MIN_W) + CARD_GAP), 1, MAX_ROWS);
        int wFit = (usableW - (cols - 1) * CARD_GAP) / cols;
        int hFit = (int) ((usableH - (rows - 1) * CARD_GAP) / (rows * CARD_ASPECT));
        int cellW = clamp(Math.min(wFit, hFit), 1, CARD_MAX_W);
        if (cellW >= CARD_MIN_W) {
            cols = clamp((usableW + CARD_GAP) / (cellW + CARD_GAP), 1, cols);
            rows = clamp((usableH + CARD_GAP) / (cardH(cellW) + CARD_GAP), 1, rows);
        }
        int cellH = cardH(cellW);
        return new Cards(cols, rows, cellW, cellH, Math.max(1, cols * rows));
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
