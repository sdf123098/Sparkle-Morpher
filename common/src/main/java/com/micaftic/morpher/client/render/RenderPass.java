package com.micaftic.morpher.client.render;

/**
 * 渲染阶段（路线图 §16 RenderContext；1.2.5 Slice A 扩展）。
 *
 * <p>替代散落的 {@code PREVIEW_MODE / FIRST_PERSON_MODE / EXTRA_PLAYER_MODE / worldRenderMode}
 * 等全局布尔标志：渲染入口显式进入某阶段，渲染管线按当前阶段决策，
 * 而不是靠全局布尔标志叠加。</p>
 *
 * <p>阶段追加在末尾，不依赖 ordinal 语义（代码中不得用 ordinal / switch 分支阶段）。</p>
 */
public enum RenderPass {
    /** 世界内实体渲染（默认，第三人称世界渲染）。 */
    WORLD,
    /** GUI 内玩家预览：额外玩家 overlay / 小图。 */
    GUI_PREVIEW,
    /** Independently controlled classic HUD preview. */
    OLD_HUD,
    /** 第一人称渲染（第一人称手 / 手持物 / 手臂模型）。 */
    FIRST_PERSON,
    /** Paper Doll（1.2.6 起落地渲染器，此处先定阶段语义）。 */
    PAPER_DOLL
}
