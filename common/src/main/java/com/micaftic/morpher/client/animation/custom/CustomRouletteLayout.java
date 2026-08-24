package com.micaftic.morpher.client.animation.custom;

import java.util.List;

/**
 * 自定义轮盘的完整布局。
 * <p>
 * 每个模型 ID 有独立的布局配置。
 * rootEntries 为根级动画（不放入子菜单），
 * groups 为子菜单分组。
 */
public record CustomRouletteLayout(
    String modelId,
    List<CustomRouletteEntry> rootEntries,
    List<CustomRouletteGroup> groups
) {}
