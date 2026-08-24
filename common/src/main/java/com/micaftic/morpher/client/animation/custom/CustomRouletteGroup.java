package com.micaftic.morpher.client.animation.custom;

import java.util.List;

/**
 * 自定义轮盘中的动画分组。
 * <p>
 * 每个分组在轮盘中显示为一个子菜单（#groupName 格式），
 * 组内的动画条目按顺序排列。
 */
public record CustomRouletteGroup(
    String name,
    List<CustomRouletteEntry> animations
) {}
