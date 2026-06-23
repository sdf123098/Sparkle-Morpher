package com.micaftic.morpher.client.animation.custom;

/**
 * 自定义轮盘中的单个动画条目。
 * <p>
 * 保存动画名、原始类别、原始索引和显示标签，
 * 用于在自定义轮盘中正确映射到服务端的 OrderedStringMap 索引。
 */
public record CustomRouletteEntry(
    String key,
    String category,
    int originalIndex,
    String displayLabel
) {}
