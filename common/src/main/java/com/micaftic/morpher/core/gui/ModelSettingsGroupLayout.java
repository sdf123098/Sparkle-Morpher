package com.micaftic.morpher.core.gui;

/** Computes the horizontally scrollable group-chip strip used by model settings. */
public final class ModelSettingsGroupLayout {
    private static final int MIN_CHIP_WIDTH = 54;

    private final int left;
    private final int viewWidth;
    private final int chipWidth;
    private final int gap;
    private final int groupCount;
    private final int contentWidth;
    private final int maxScroll;

    private ModelSettingsGroupLayout(int left, int viewWidth, int chipWidth, int gap, int groupCount,
                                     int contentWidth, int maxScroll) {
        this.left = left;
        this.viewWidth = viewWidth;
        this.chipWidth = chipWidth;
        this.gap = gap;
        this.groupCount = groupCount;
        this.contentWidth = contentWidth;
        this.maxScroll = maxScroll;
    }

    public static ModelSettingsGroupLayout create(int left, int right, int groupCount, int gap) {
        int viewWidth = Math.max(0, right - left);
        int safeCount = Math.max(0, groupCount);
        int safeGap = Math.max(0, gap);
        int chipWidth = safeCount == 0
                ? 0
                : Math.max(MIN_CHIP_WIDTH, (viewWidth - safeGap * Math.max(0, safeCount - 1)) / safeCount);
        int contentWidth = safeCount == 0 ? 0 : safeCount * chipWidth + safeGap * (safeCount - 1);
        return new ModelSettingsGroupLayout(left, viewWidth, chipWidth, safeGap, safeCount,
                contentWidth, Math.max(0, contentWidth - viewWidth));
    }

    public int contentWidth() {
        return contentWidth;
    }

    public int maxScroll() {
        return maxScroll;
    }

    public int chipWidth() {
        return chipWidth;
    }

    public int chipWidth(int index) {
        if (maxScroll == 0 && index == groupCount - 1) {
            return Math.max(0, viewWidth - (groupCount - 1) * (chipWidth + gap));
        }
        return chipWidth;
    }

    public int chipLeft(int index, int scroll) {
        int clampedScroll = Math.max(0, Math.min(maxScroll, scroll));
        return left + index * (chipWidth + gap) - clampedScroll;
    }
}
