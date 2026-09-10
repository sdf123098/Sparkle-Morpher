package com.micaftic.morpher.client.render;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import com.micaftic.morpher.core.compat.firstperson.FirstPersonCompat;
import com.micaftic.morpher.core.compat.oculus.OculusCompat;

/**
 * 渲染上下文（路线图 §16 RenderContext；1.2.5 Slice A/C 扩展）。
 *
 * <p>渲染入口用 {@link #enterScope(RenderScope)} 显式进入某渲染阶段并在 finally 中
 * {@link #restoreScope(RenderScope)} 恢复，替代旧布尔开关（后者在异常路径下会残留状态，
 * 污染后续世界渲染）。上下文携带阶段、模型预览标志、当前渲染实体与 partialTick，
 * 供渲染管线与 molang / physics 求值按阶段决策。</p>
 *
 * <p>维度彼此正交：{@code pass}（最多一个主阶段）与 {@code modelPreview}（模型预览布尔，
 * 等价旧 {@code PREVIEW_MODE} ThreadLocal）互不覆盖——切换 pass 不会清掉预览标志，
 * 反之亦然；这与 1.2.1 起 GUI 预览同时设置 preview 与 extra-player 的既有行为一致。</p>
 *
 * <p>线程语义：上下文挂在当前线程（渲染线程）。动画评估若跑在 worker 线程（世界渲染期间），
 * worker 线程读到的是该线程的默认 {@link RenderScope#world()}——世界阶段布尔标志（worldRenderMode）
 * 的跨线程迁移属 1.2.5 后续 Slice，不在本 Slice 行为变更范围内。</p>
 *
 * <p>兼容：仅切阶段的旧入口 {@link #enter(RenderPass)} / {@link #restore(RenderPass)} 保留，
 * 进入的是不带 entity / partialTick 的裸 scope（保留当前 modelPreview 标志）；
 * 需要携带实体状态时请用 scope 入口。</p>
 */
public final class RenderContext {

    /** 当前渲染阶段 + 模型预览标志 + 渲染实体 + partialTick（不可变）。 */
    public record RenderScope(RenderPass pass, boolean modelPreview, @Nullable Entity entity, float partialTick) {

        /** 兼容 3 参构造：默认非模型预览。 */
        public RenderScope(RenderPass pass, @Nullable Entity entity, float partialTick) {
            this(pass, false, entity, partialTick);
        }

        public static RenderScope world() {
            return new RenderScope(RenderPass.WORLD, false, null, 0.0f);
        }

        public RenderScope withModelPreview(boolean preview) {
            return new RenderScope(pass, preview, entity, partialTick);
        }

        public RenderScope withPass(RenderPass newPass) {
            return new RenderScope(newPass, modelPreview, entity, partialTick);
        }
    }

    private static final ThreadLocal<RenderScope> CURRENT =
            ThreadLocal.withInitial(RenderScope::world);

    /**
     * 物理域（路线图 §21）：决定 {@code GeoEntity} 使用哪套 {@code PhysicsManager}，
     * 替代散落的 {@code ModelPreviewRenderer} 全局 flag 读取。优先级与历史行为一致。
     */
    public enum PhysicsDomain {
        /** 世界内实体（默认）。 */
        WORLD,
        /** 模型预览（旧 PREVIEW_MODE）——独立 preview 物理域。 */
        PREVIEW,
        /** GUI 额外玩家 overlay / OLD_HUD——独立 extra-player 物理域。 */
        EXTRA_PLAYER,
        /** 第一人称（含 PBR / FirstPerson mod 等 compat 渲染模式）——复用世界物理域。 */
        FIRST_PERSON
    }

    private RenderContext() {
    }

    /**
     * 当前物理域。compat 渲染模式（PBR / FirstPerson mod）并入 {@link PhysicsDomain#FIRST_PERSON}，
     * 与 {@code ModelPreviewRenderer.isFirstPerson()} 的历史语义保持一致（该判定在渲染线程与
     * worker 线程均需可见，故此处读取 compat 全局状态而非仅 ThreadLocal pass）。
     */
    public static PhysicsDomain physicsDomain() {
        RenderScope scope = CURRENT.get();
        if (scope.modelPreview()) {
            return PhysicsDomain.PREVIEW;
        }
        RenderPass pass = scope.pass();
        if (pass == RenderPass.GUI_PREVIEW || pass == RenderPass.OLD_HUD) {
            return PhysicsDomain.EXTRA_PLAYER;
        }
        if (pass == RenderPass.FIRST_PERSON || compatFirstPersonActive()) {
            return PhysicsDomain.FIRST_PERSON;
        }
        return PhysicsDomain.WORLD;
    }

    /**
     * compat 渲染模式（PBR / FirstPerson mod）视作第一人称；探测在 loader 未就绪等异常情况下
     * 按非第一人称处理，避免渲染上下文查询因 compat 探测失败而崩溃。
     */
    private static boolean compatFirstPersonActive() {
        try {
            return OculusCompat.isPBRActive() || FirstPersonCompat.isFirstPersonActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 进入某渲染阶段（不带实体/partialTick），返回上一阶段（供 finally 恢复）。保留模型预览标志。 */
    public static RenderPass enter(RenderPass pass) {
        RenderScope current = CURRENT.get();
        CURRENT.set(new RenderScope(pass, current.modelPreview(), null, 0.0f));
        return current.pass();
    }

    /** 恢复上一阶段（不带实体/partialTick）。保留模型预览标志。 */
    public static void restore(RenderPass previous) {
        RenderScope current = CURRENT.get();
        CURRENT.set(new RenderScope(previous, current.modelPreview(), null, 0.0f));
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

    /** 设置模型预览标志（等价旧 {@code PREVIEW_MODE} ThreadLocal），不影响 pass。 */
    public static void setModelPreview(boolean preview) {
        CURRENT.set(CURRENT.get().withModelPreview(preview));
    }

    /** 是否处于模型预览（旧 {@code ModelPreviewRenderer.isPreview()}）。 */
    public static boolean isModelPreview() {
        return CURRENT.get().modelPreview();
    }

    /** 当前渲染实体（world 默认 / 裸 pass scope 下为 null）。 */
    @Nullable
    public static Entity currentEntity() {
        return CURRENT.get().entity();
    }

    public static float partialTick() {
        return CURRENT.get().partialTick();
    }

    /** 模型预览或 GUI 预览的并集（行为等价 {@code isPreview() || isGuiPreview()}）。 */
    public static boolean isAnyPreview() {
        return isModelPreview() || isGuiPreview();
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
