package com.micaftic.morpher.core.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.render.RenderContext;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.core.compat.oculus.OculusCompat;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 26.2 世界渲染的 GPU 蒙皮「in-pipeline」通道（路线图 §20 Level A）。
 *
 * <h2>问题</h2>
 * 26.2 的实体渲染走 {@code SubmitNodeCollector} 延迟提交：提交阶段只登记几何，真正绘制发生在
 * {@code FeatureRenderDispatcher} 执行 feature phase 时。因此在提交阶段直接建 render pass 画到主
 * 目标会绕过 MC 的 framegraph 调度（pass 顺序 / 资源生命周期 / barrier 都不受管）——这正是旧直绘
 * 路径必须在 {@code hasSubmitContext} 时被拒绝的原因。
 *
 * <h2>本类做法</h2>
 * 提交阶段把 GPU 绘制请求<b>登记</b>下来（{@link #tryDefer}）；稍后在构建帧图时把它作为
 * <b>一等 framegraph pass</b> 插入（{@link #appendPass}），声明读写 {@code targets.main}。于是
 * framegraph 负责把它排在 main/alwaysOnTop 之后、并管理资源生命周期与 barrier；执行体
 * （{@link #renderAll}）在正确时机调用既有的 {@link Blaze3DRenderPath#tryRender}。
 *
 * <h2>阴影为什么不丢</h2>
 * 玩家阴影由 {@code EntityRenderDispatcher.submit} 通过 {@code submitShadow(ShadowPiece)} 独立
 * 提交（纯数据：相对位置 + 形状 + alpha，不含模型几何），而模组只取消
 * {@code LivingEntityRenderer.submit}。故本通道即使不向 collector 提交几何也不会丢阴影。
 *
 * <h2>开关与安全</h2>
 * 由模组配置项 {@code EnableBlaze3DInPipelineDraw} 控制（游戏内「模型面板 → 性能」可切换，
 * 无需任何 JVM 参数），默认<b>关闭</b>：该路径仍需实机验证（framegraph pass 内自建 render pass
 * 的语义、与光影/描边的交互）。关闭时 {@link #tryDefer} 恒返回 false，调用方走既有路径，
 * 行为与改动前一致。
 * 所有异常都被吞掉并清空本帧待绘，绝不向上抛。
 */
public final class Blaze3DModelFramePass {

    /**
     * 本通道是否启用。读取模组配置（{@code EnableBlaze3DInPipelineDraw}），默认关闭；
     * 配置未注册/不可读时按关闭处理（见 {@code ConfigPolicies.bool} 的 fallback），
     * 保证默认行为与未引入本通道时完全一致。
     *
     * <p>用户在游戏内把开关关掉再打开时重新武装（清除 {@link #autoDisabled}），
     * 便于在不重启的情况下重试。</p>
     */
    public static boolean isEnabled() {
        boolean enabled = com.micaftic.morpher.core.config.ConfigPolicies.graphics().blaze3dInPipelineDraw();
        if (!enabled) {
            // 开关关闭 = 明确重置自愈状态；下次打开即为全新尝试。
            if (autoDisabled) {
                autoDisabled = false;
                reportedAutoDisable.set(false);
            }
            return false;
        }
        return true;
    }

    private static final String PASS_NAME = "sparkle_morpher_blaze3d_models";

    /** 渲染线程帧内待绘列表（提交阶段填充，framegraph pass 消费）。 */
    private static final List<Draw> PENDING = new ArrayList<>();

    private static final AtomicBoolean warnedSubmitFailure = new AtomicBoolean(false);
    private static final AtomicBoolean warnedRenderFailure = new AtomicBoolean(false);
    /** 一次性状态日志标记：让用户在默认日志级别就能确认本通道是否真的生效（不受 gpuDebugLog 门控）。 */
    private static final AtomicBoolean reportedFirstTakeover = new AtomicBoolean(false);
    private static final AtomicBoolean reportedFirstDraw = new AtomicBoolean(false);
    private static final AtomicBoolean reportedFirstRefused = new AtomicBoolean(false);

    /**
     * 自愈开关：若某帧登记了延迟绘制、但帧图 pass 没有执行（几何已从 collector 摘除 →
     * 模型会不可见），立即永久停用本通道，让后续帧走既有提交路径，模型恢复可见。
     *
     * <p>2026-09-11 实测：某些环境下帧图 pass 未被执行，导致「大部分模型在世界内不渲染」。
     * 该开关保证该缺陷最多影响一帧，且日志给出确切阶段，避免无声丢模型。
     * 配置项由开→关再开时重新武装（见 {@link #isEnabled()}）。</p>
     */
    private static volatile boolean autoDisabled;
    private static final AtomicBoolean reportedAutoDisable = new AtomicBoolean(false);

    /** 本帧统计：登记数 / 是否成功挂入帧图 / pass 是否执行 / 实际画出数。 */
    private static int deferredThisFrame;
    private static boolean appendedThisFrame;
    private static boolean executedThisFrame;
    private static int renderedThisFrame;

    private Blaze3DModelFramePass() {
    }

    /** 帧开始：清空上一帧残留并重置本帧统计。由 {@code WorldRendererMixin} 在 render HEAD 调用。 */
    public static void beginFrame() {
        PENDING.clear();
        deferredThisFrame = 0;
        appendedThisFrame = false;
        executedThisFrame = false;
        renderedThisFrame = 0;
    }

    /**
     * 帧结束兜底清理 + 自愈判定。由 {@code WorldRendererMixin} 在 render RETURN 调用。
     *
     * <p>若本帧登记了绘制却从未被画出（pass 未跑，或跑在几何被清空之后），说明几何已从
     * collector 摘除但没画出来 → 模型不可见。此时永久停用本通道并打印一次明确告警，
     * 让该缺陷最多影响一帧。</p>
     */
    public static void endFrame() {
        if (deferredThisFrame > 0 && renderedThisFrame < deferredThisFrame) {
            autoDisabled = true;
            if (reportedAutoDisable.compareAndSet(false, true)) {
                YesSteveModel.LOGGER.warn(
                        "[SM-BLAZE3D] in-pipeline draw LOST geometry this frame "
                                + "(deferred={}, rendered={}, appendedToFrameGraph={}, passExecuted={}). "
                                + "Models would have been invisible, so in-pipeline draw is now AUTO-DISABLED "
                                + "for this session; rendering falls back to the normal submit path. "
                                + "Toggle EnableBlaze3DInPipelineDraw off/on (or restart) to re-arm.",
                        deferredThisFrame, renderedThisFrame, appendedThisFrame, executedThisFrame);
            }
        }
        PENDING.clear();
        deferredThisFrame = 0;
        appendedThisFrame = false;
        executedThisFrame = false;
        renderedThisFrame = 0;
    }

    /**
     * 提交阶段登记一次 GPU 绘制。仅在最保守场景接管：
     * 开关启用 + 既有实验开关开启 + 世界渲染 + 非预览 + 非第一人称 + 非光影 + 不透明 + 有骨骼。
     * 其余情况一律返回 false，调用方走既有提交路径。
     *
     * @return true = 已登记（调用方<span>不得</span>再走原路径）；false = 未接管
     */
    public static boolean tryDefer(
            GeoModel model,
            PoseStack.Pose pose,
            float[] boneParams,
            float[] stateBuffer,
            int textureIndex,
            int renderPartMask,
            int packedLight,
            int packedOverlay,
            float red, float green, float blue, float alpha,
            Identifier textureLocation,
            boolean entityGlowing) {
        if (!isEnabled()) {
            return false;
        }
        // 自愈：一旦本通道被判定为「登记了但 pass 不执行」，立刻退回既有路径，避免持续丢模型。
        if (autoDisabled) {
            return false;
        }
        try {
            if (!Blaze3DRenderPath.isExperimentalEnabled()) {
                return false;
            }
            if (model == null || pose == null || textureLocation == null) {
                return false;
            }
            if (model.bakedBones == null || model.bakedBones.isEmpty()) {
                return false;
            }
            // 保守门控：首启只覆盖最普通的世界第三人称不透明玩家模型。
            if (RenderContext.isAnyPreview() || ModelPreviewRenderer.isFirstPerson()
                    || !ModelPreviewRenderer.isWorldRender()) {
                return false;
            }
            if (OculusCompat.isShaderPackInUse()) {
                return false;
            }
            // 发光实体需要把几何画进 outline 目标（由提交的模型几何派生），
            // 本通道不提交几何，故发光时回退既有路径以保留描边。
            if (entityGlowing) {
                return false;
            }
            // 半透明走既有路径（phase 混合语义需实机确认后再放开）。
            if (model.isTranslucentTexture(textureIndex)) {
                return false;
            }
            // 发光拆分（NON_GLOW/GLOW）需要两次 pass，走既有路径。
            if (renderPartMask != 0) {
                return false;
            }

            if (reportedFirstTakeover.compareAndSet(false, true)) {
                YesSteveModel.LOGGER.info("[SM-BLAZE3D] in-pipeline GPU draw is ACTIVE (config EnableBlaze3DInPipelineDraw=on); draws are registered at submit time and executed by a {} pass. Deferred draws are not submitted to the collector, so glow/outline entities and transparent models still use the normal path.",
                        PASS_NAME);
            }
            PENDING.add(new Draw(
                    model,
                    pose.copy(),   // 提交阶段之后 Pose 会被复用，必须立即深拷贝
                    boneParams,
                    stateBuffer,
                    renderPartMask,
                    packedLight,
                    packedOverlay,
                    red, green, blue, alpha,
                    textureLocation));
            deferredThisFrame++;
            return true;
        } catch (Throwable t) {
            if (warnedSubmitFailure.compareAndSet(false, true)) {
                GpuDebugLog.warn("Blaze3D in-pipeline submit failed (using immediate path): {}", t.toString());
            }
            return false;
        }
    }

    /**
     * 把本帧待绘插入 MC 帧图。由 mixin 在 {@code LevelRenderer.addAlwaysOnTopPass} 之后调用——
     * 那时 {@code targets.main} 已被 main/alwaysOnTop pass 更新为最新 handle，我们据此排在它们之后。
     */
    public static void appendPass(FrameGraphBuilder builder, LevelTargetBundle targets) {
        // 这里不再查开关：只要本帧已登记绘制（tryDefer 通过）就必须执行，否则用户在游戏内
        // 切换开关的瞬间会丢一帧模型。开关只由 tryDefer 决定是否接管。
        if (builder == null || targets == null || PENDING.isEmpty()) {
            return;
        }
        try {
            FramePass pass = builder.addPass(PASS_NAME);
            // 声明读写主目标：framegraph 据此排序并管理资源生命周期/barrier。
            // readsAndWrites 返回新 handle，必须写回 bundle（与 vanilla 各 pass 一致）。
            ResourceHandle<RenderTarget> main = pass.readsAndWrites(targets.main);
            targets.main = main;
            pass.executes(Blaze3DModelFramePass::renderAll);
            appendedThisFrame = true;
        } catch (Throwable t) {
            if (warnedRenderFailure.compareAndSet(false, true)) {
                GpuDebugLog.warn("Blaze3D in-pipeline pass registration failed (dropping {} deferred draws): {}",
                        PENDING.size(), t.toString());
            }
            // 挂载失败 = 本帧这些绘制不会执行 → 立即自愈，避免下一帧继续丢模型。
            autoDisabled = true;
            if (reportedAutoDisable.compareAndSet(false, true)) {
                YesSteveModel.LOGGER.warn(
                        "[SM-BLAZE3D] could not register the in-pipeline frame pass ({}); AUTO-DISABLED for this "
                                + "session, rendering falls back to the normal submit path.",
                        t.toString());
            }
            PENDING.clear();
        }
    }

    /** framegraph pass 执行体：在正确时机调用既有 GPU 蒙皮绘制。 */
    private static void renderAll() {
        executedThisFrame = true;
        renderedThisFrame = PENDING.size();
        if (PENDING.isEmpty()) {
            return;
        }
        if (reportedFirstDraw.compareAndSet(false, true)) {
            YesSteveModel.LOGGER.info("[SM-BLAZE3D] in-pipeline pass executed: {} deferred draw(s) rendering via Blaze3DRenderPath", PENDING.size());
        }
        int drawn = 0;
        try {
            for (Draw draw : PENDING) {
                if (Blaze3DRenderPath.tryRender(
                        draw.model(), draw.pose(), draw.boneParams(), draw.stateBuffer(),
                        draw.renderPartMask(), draw.packedLight(), draw.packedOverlay(),
                        draw.red(), draw.green(), draw.blue(), draw.alpha(),
                        draw.textureLocation(), false)) {
                    drawn++;
                }
            }
        } catch (Throwable t) {
            if (warnedRenderFailure.compareAndSet(false, true)) {
                GpuDebugLog.warn("Blaze3D in-pipeline draw failed: {}", t.toString());
            }
        } finally {
            if (drawn < PENDING.size() && reportedFirstRefused.compareAndSet(false, true)) {
                YesSteveModel.LOGGER.warn(
                        "[SM-BLAZE3D] in-pipeline pass drew only {}/{} deferred model(s); the rest were refused by "
                                + "Blaze3DRenderPath. Enable GpuDebugLog for the per-draw reason. (Refused draws were "
                                + "already removed from the collector, so those models will not appear this frame.)",
                        drawn, PENDING.size());
            }
            PENDING.clear();
        }
    }

    /** 一次延迟绘制所需的全部数据（pose 已深拷贝）。 */
    private record Draw(
            GeoModel model,
            PoseStack.Pose pose,
            float[] boneParams,
            float[] stateBuffer,
            int renderPartMask,
            int packedLight,
            int packedOverlay,
            float red, float green, float blue, float alpha,
            Identifier textureLocation
    ) {
    }
}
