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
    /** GUI 内通用模型预览，可保留预览界面的鼠标看向行为。 */
    GUI_PREVIEW,
    /** 现有的旧 HUD 渲染：固定展示朝向，与世界摄像机头部旋转隔离。 */
    OLD_HUD
}
