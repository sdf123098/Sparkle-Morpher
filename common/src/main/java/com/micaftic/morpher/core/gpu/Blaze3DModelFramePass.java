package com.micaftic.morpher.core.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
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
     */
    public static boolean isEnabled() {
        return com.micaftic.morpher.core.config.ConfigPolicies.graphics().blaze3dInPipelineDraw();
    }

    private static final String PASS_NAME = "sparkle_morpher_blaze3d_models";

    /** 渲染线程帧内待绘列表（提交阶段填充，framegraph pass 消费）。 */
    private static final List<Draw> PENDING = new ArrayList<>();

    private static final AtomicBoolean warnedSubmitFailure = new AtomicBoolean(false);
    private static final AtomicBoolean warnedRenderFailure = new AtomicBoolean(false);

    private Blaze3DModelFramePass() {
    }

    /** 帧开始：清空上一帧残留。由 {@code WorldRendererMixin} 在 render HEAD 调用。 */
    public static void beginFrame() {
        PENDING.clear();
    }

    /** 帧结束兜底清理。由 {@code WorldRendererMixin} 在 render RETURN 调用。 */
    public static void endFrame() {
        PENDING.clear();
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
        } catch (Throwable t) {
            if (warnedRenderFailure.compareAndSet(false, true)) {
                GpuDebugLog.warn("Blaze3D in-pipeline pass registration failed (dropping {} deferred draws): {}",
                        PENDING.size(), t.toString());
            }
            PENDING.clear();
        }
    }

    /** framegraph pass 执行体：在正确时机调用既有 GPU 蒙皮绘制。 */
    private static void renderAll() {
        if (PENDING.isEmpty()) {
            return;
        }
        try {
            for (Draw draw : PENDING) {
                Blaze3DRenderPath.tryRender(
                        draw.model(), draw.pose(), draw.boneParams(), draw.stateBuffer(),
                        draw.renderPartMask(), draw.packedLight(), draw.packedOverlay(),
                        draw.red(), draw.green(), draw.blue(), draw.alpha(),
                        draw.textureLocation(), false);
            }
        } catch (Throwable t) {
            if (warnedRenderFailure.compareAndSet(false, true)) {
                GpuDebugLog.warn("Blaze3D in-pipeline draw failed: {}", t.toString());
            }
        } finally {
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
