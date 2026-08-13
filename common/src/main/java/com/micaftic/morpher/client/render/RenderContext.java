package com.micaftic.morpher.client.render;

/**
 * 渲染上下文（路线图 §16 雏形，提前落地额外玩家部分）。
 *
 * <p>本次先收口 {@code EXTRA_PLAYER_MODE} ThreadLocal → {@link RenderPass#GUI_PREVIEW}：
 * 渲染入口用 {@link #enter(RenderPass)} 显式进入阶段并在 finally 中 {@link #restore(RenderPass)}，
 * 替代原先 setExtraPlayerMode(true)/setExtraPlayerMode(false) 的布尔开关（后者在异常路径下
 * 会残留状态，污染后续世界渲染）。</p>
 *
 * <p>后续 1.2.6 按路线图扩展为完整 RenderContext（pass/entity/partialTick/camera 等），
 * 并迁移 WORLD / FIRST_PERSON / PAPER_DOLL 各阶段。</p>
 */
public final class RenderContext {

    private static final ThreadLocal<RenderPass> CURRENT_PASS = ThreadLocal.withInitial(() -> RenderPass.WORLD);

    private RenderContext() {
    }

    /** 进入某渲染阶段，返回上一阶段（供 finally 恢复）。 */
    public static RenderPass enter(RenderPass pass) {
        RenderPass previous = CURRENT_PASS.get();
        CURRENT_PASS.set(pass);
        return previous;
    }

    /** 恢复上一阶段。 */
    public static void restore(RenderPass previous) {
        CURRENT_PASS.set(previous);
    }

    public static RenderPass currentPass() {
        return CURRENT_PASS.get();
    }

    /** 是否处于 GUI 内玩家预览（额外玩家 overlay）。 */
    public static boolean isGuiPreview() {
        RenderPass pass = CURRENT_PASS.get();
        return pass == RenderPass.GUI_PREVIEW || pass == RenderPass.OLD_HUD;
    }

    /** 是否正在绘制现有旧 HUD；该阶段不得继承世界摄像机的头部朝向。 */
    public static boolean isOldHud() {
        return CURRENT_PASS.get() == RenderPass.OLD_HUD;
    }
}
