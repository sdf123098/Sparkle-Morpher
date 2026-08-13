package com.micaftic.morpher.client.render;

/**
 * 渲染阶段（路线图 §16 RenderContext 雏形，提前落地额外玩家部分）。
 *
 * <p>替代散落的 {@code PREVIEW_MODE / EXTRA_PLAYER_MODE} 等 ThreadLocal 布尔标志：
 * 渲染入口（如额外玩家 overlay）显式进入某阶段，渲染管线按当前阶段决策，
 * 而不是靠全局布尔标志叠加。</p>
 */
public enum RenderPass {
    /** 世界内实体渲染（默认）。 */
    WORLD,
    /** GUI 内玩家预览：额外玩家 overlay / 小图。 */
    GUI_PREVIEW
}
