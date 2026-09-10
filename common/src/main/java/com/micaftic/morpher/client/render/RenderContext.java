package com.micaftic.morpher.client.render;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * 渲染上下文（路线图 §16 RenderContext；1.2.5 Slice A 扩展）。
 *
 * <p>渲染入口用 {@link #enterScope(RenderScope)} 显式进入某渲染阶段并在 finally 中
 * {@link #restoreScope(RenderScope)} 恢复，替代旧布尔开关（后者在异常路径下会残留状态，
 * 污染后续世界渲染）。上下文携带阶段、当前渲染实体与 partialTick，供渲染管线与
 * molang / physics 求值按阶段决策。</p>
 *
 * <p>线程语义：上下文挂在当前线程（渲染线程）。动画评估若跑在 worker 线程（世界渲染期间），
 * worker 线程读到的是该线程的默认 {@link RenderScope#world()}——世界阶段布尔标志（worldRenderMode）
 * 的跨线程迁移属 1.2.5 后续 Slice，不在本 Slice 行为变更范围内。</p>
 *
 * <p>兼容：仅切阶段的旧入口 {@link #enter(RenderPass)} / {@link #restore(RenderPass)} 保留，
 * 但进入的是不带 entity / partialTick 的裸 scope；需要携带实体状态时请用 scope 入口。</p>
 */
public final class RenderContext {

    /** 当前渲染阶段 + 渲染实体 + partialTick（不可变）。 */
    public record RenderScope(RenderPass pass, @Nullable Entity entity, float partialTick) {
        public static RenderScope world() {
            return new RenderScope(RenderPass.WORLD, null, 0.0f);
        }
    }

    private static final ThreadLocal<RenderScope> CURRENT =
            ThreadLocal.withInitial(RenderScope::world);

    private RenderContext() {
    }

    /** 进入某渲染阶段（不带实体/partialTick），返回上一阶段（供 finally 恢复）。 */
    public static RenderPass enter(RenderPass pass) {
        RenderPass previous = CURRENT.get().pass();
        CURRENT.set(new RenderScope(pass, null, 0.0f));
        return previous;
    }

    /** 恢复上一阶段（不带实体/partialTick）。 */
    public static void restore(RenderPass previous) {
        CURRENT.set(new RenderScope(previous, null, 0.0f));
    }

    /** 进入完整渲染 scope，返回上一 scope（供 finally 恢复）。 */
    public static RenderScope enterScope(RenderScope scope) {
        RenderScope previous = CURRENT.get();
        CURRENT.set(scope);
        return previous;
    }

    /** 进入完整渲染 scope（显式字段），返回上一 scope。 */
    public static RenderScope enterScope(RenderPass pass, @Nullable Entity entity, float partialTick) {
        return enterScope(new RenderScope(pass, entity, partialTick));
    }

    /** 恢复上一 scope。 */
    public static void restoreScope(RenderScope previous) {
        CURRENT.set(previous);
    }

    public static RenderScope currentScope() {
        return CURRENT.get();
    }

    public static RenderPass currentPass() {
        return CURRENT.get().pass();
    }

    /** 当前渲染实体（world 默认 / 裸 pass scope 下为 null）。 */
    @Nullable
    public static Entity currentEntity() {
        return CURRENT.get().entity();
    }

    public static float partialTick() {
        return CURRENT.get().partialTick();
    }

    /** 是否处于 GUI 内玩家预览（额外玩家 overlay / OLD_HUD）。 */
    public static boolean isGuiPreview() {
        RenderPass pass = CURRENT.get().pass();
        return pass == RenderPass.GUI_PREVIEW || pass == RenderPass.OLD_HUD;
    }

    public static boolean isOldHud() {
        return CURRENT.get().pass() == RenderPass.OLD_HUD;
    }

    public static boolean isFirstPerson() {
        return CURRENT.get().pass() == RenderPass.FIRST_PERSON;
    }

    public static boolean isPaperDoll() {
        return CURRENT.get().pass() == RenderPass.PAPER_DOLL;
    }
}
