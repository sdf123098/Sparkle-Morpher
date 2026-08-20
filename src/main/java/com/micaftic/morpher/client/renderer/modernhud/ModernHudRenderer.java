package com.micaftic.morpher.client.renderer.modernhud;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.config.ExtraPlayerRenderConfig;
import com.micaftic.morpher.config.HudLayoutConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 现代 HUD 入口（阶段 2，计划书 §5 阶段 2；26.2 移植）。
 *
 * <p>消费世界帧共享姿态快照（{@link ModernHudPoseStore}，不触发第二次动画评估），
 * 经 {@link ModernHudRenderInstance} 提交专用 GPU 主体到独立 FBO，再合成到 GUI
 * （26.2 用 {@code GuiGraphicsExtractor.blit(GpuTextureView, GpuSampler, ...)}，
 * 走 RenderPipelines.GUI_TEXTURED 标准混合管线）。
 * 成功提交后返回 true —— {@code ExtraPlayerOverlay} 据此跳过经典 HUD，保证两个
 * HUD 不重叠绘制。
 *
 * <p>26.2 无 1.21.1 的 graphics.flush() 概念（retained GUI 提取模型），FBO 提交是
 * CommandEncoder 立即执行；26.2 的批处理隔离由 RenderBackendDecision/SubmitRenderContext
 * 体系承担（渲染实例侧门控）。
 */
public final class ModernHudRenderer {
    /** FBO 内模型 maxX/maxY 边的对齐逻辑坐标（与 {@link ModernHudRenderInstance#FBO_PADDING} 保持一致）。 */
    private static final int FBO_PADDING = 8;
    private static final AtomicBoolean warnedRender = new AtomicBoolean(false);
    private static final AtomicLong submittedCount = new AtomicLong();

    // RenderGuiEvent.Post 每帧触发次数按版本/模组组合可能多于 1：用 GuiGraphicsExtractor
    // 实例引用做帧去重，首次调用真正渲染并缓存结果，同帧后续调用直接返回缓存值。
    // 布局编辑器预览走 renderAt（显式参数），不经过本缓存。
    private static GuiGraphicsExtractor dedupeGraphics;
    private static boolean dedupeResult;

    private ModernHudRenderer() {
    }

    private static void diag(String reason) {
        if (warnedRender.compareAndSet(false, true)) {
            YesSteveModel.LOGGER.warn("[MODERN-HUD] render skipped: {}", reason);
        }
    }

    /** Returns true only after the modern renderer has fully drawn and composited this frame. */
    public static boolean render(GuiGraphicsExtractor graphics, LocalPlayer player, float partialTick,
                                 int screenWidth, int screenHeight) {
        // 每帧只真正执行一次（同帧多 Post 事件去重）；无快照自评估/FBO 重绘成本随之降到 1 次/帧。
        // 位置/缩放/yaw 读现代 HUD 独立布局（与经典 HUD 分离，见 ExtraPlayerRenderConfig.MODERN_HUD_LAYOUT）。
        if (dedupeGraphics == graphics) {
            return dedupeResult;
        }
        HudLayoutConfig layout = ExtraPlayerRenderConfig.MODERN_HUD_LAYOUT;
        boolean result = renderAt(graphics, player, partialTick, screenWidth, screenHeight,
                layout.getX(), layout.getY(), layout.getScale(), layout.getYaw());
        dedupeGraphics = graphics;
        dedupeResult = result;
        return result;
    }

    /** 布局编辑器预览入口：位置/缩放/yaw 由调用方给定（编辑中未保存的值）。 */
    public static boolean renderAt(GuiGraphicsExtractor graphics, LocalPlayer player, float partialTick,
                                   int screenWidth, int screenHeight,
                                   float x, float y, float scale, float yawOffset) {
        PlayerPoseSnapshot snapshot = ModernHudPoseStore.consume();
        if (snapshot == null) {
            // 本地玩家不可见/第一人称：世界不发布快照 → 现代 HUD 自评估一次
            // （与经典 HUD 同款 OLD_HUD 上下文；第一人称时世界未评估，此为首次评估）
            snapshot = ModernHudPoseStore.buildFallbackSnapshot(player, partialTick);
            if (snapshot == null) {
                diag("no snapshot and fallback unavailable (world eval=" + ModernHudPoseStore.worldEvalCount() + ")");
                return false;
            }
        }
        String currentModelId = PlayerCapability.get(player)
                .map(PlayerCapability::getModelId)
                .orElse(null);
        if (!Objects.equals(snapshot.modelId(), currentModelId)) {
            diag("model mismatch snapshot=" + snapshot.modelId() + " current=" + currentModelId);
            return false;
        }
        if (!snapshot.isRenderable()) {
            diag("snapshot not renderable");
            return false;
        }
        if (scale <= 0.0f) {
            diag("scale <= 0");
            return false;
        }

        ModernHudRenderInstance instance = ModernHudRenderInstance.getOrCreate(snapshot.modelId(), snapshot.model().getGeoModel());
        boolean submitted = instance.tryRender(snapshot, FBO_PADDING, FBO_PADDING,
                scale, yawOffset, partialTick);
        if (!submitted) {
            diag("instance.tryRender failed modelId=" + snapshot.modelId());
            return false;
        }
        composite(graphics, instance, x, y, scale);
        long count = submittedCount.incrementAndGet();
        if (count % 600 == 1) {
            YesSteveModel.LOGGER.info("[MODERN-HUD] submitted {} times (world eval={}, hud consume={})",
                    count, ModernHudPoseStore.worldEvalCount(), ModernHudPoseStore.hudConsumeCount());
        }
        return true;
    }

    /**
     * 把独立 HUD FBO 透明合成到 GUI 对应矩形。26.2 用 GuiGraphicsExtractor.blit(GpuTextureView, ...)
     * 走 GUI_TEXTURED 混合管线；UV 沿用 1.21.1 的约定（FBO 纹理 v=0 在矩形底部，即 v0=1/v1=0），
     * 若游戏内发现上下颠倒，把 (v0, v1) 换成 (0, 1) 即可。
     */
    private static void composite(GuiGraphicsExtractor graphics, ModernHudRenderInstance instance, float posX, float posY, float scale) {
        GpuTextureView view = instance.fboColorView();
        if (view == null) {
            return;
        }
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        // Anchor-based placement：用户 (X,Y) 是“标准 scale × 2*scale 人物框”的左上角，与布局编辑器/经典 HUD
        // 同一语义。人物锚点（模型原点，脚底中心附近）应对齐到与经典 HUD 相同的参考点：
        //   横 = 框中心 X + scale*0.5
        //   纵 = 框底附近 Y + scale*2 - 2
        // FBO 大小只决定“能容纳多大模型”，不再参与位置语义 → 无论 FBO 是 40×80、245×304 还是因翅膀变成
        // 500×500，用户设置的人物锚点始终落在同一个 GUI 位置（Scale 变化不漂移）。
        float desiredAnchorX = posX + scale * 0.5f;
        float desiredAnchorY = posY + scale * 2.0f - 2.0f;
        float x0 = desiredAnchorX - instance.anchorX();
        float y0 = desiredAnchorY - instance.anchorY();
        float x1 = x0 + instance.fboLogicalWidth();
        float y1 = y0 + instance.fboLogicalHeight();
        // 26.1.2 世界内 HUD extractor 的 pose 可能带缩放+平移（vanilla Gui.extractRenderState
        // 全程不改 pose，此状态来自第三方 HUD 元素 push 后未归位；编辑器 Screen 阶段为单位阵）。
        // 顶点 = pose * 传入坐标；为使结果等于布局逻辑坐标，传入坐标预乘 pose 的逆。
        // pose 退化（determinant≈0，更异常的泄漏）时放弃补偿按原坐标绘制——invert() 会抛
        // ArithmeticException，经 fabric HUD 回调传进 vanilla HUD 提取会炸掉整个 GUI 帧。
        org.joml.Matrix3x2f pose = graphics.pose();
        org.joml.Matrix3x2f inv = new org.joml.Matrix3x2f(pose);
        boolean compensate = Math.abs(inv.determinant()) > 1e-6f;
        if (compensate) {
            inv.invert();
        } else {
            inv.identity();
        }
        float ax0 = inv.m00() * x0 + inv.m01() * y0 + inv.m20();
        float ay0 = inv.m10() * x0 + inv.m11() * y0 + inv.m21();
        float ax1 = inv.m00() * x1 + inv.m01() * y1 + inv.m20();
        float ay1 = inv.m10() * x1 + inv.m11() * y1 + inv.m21();
        graphics.blit(view, sampler, Math.round(ax0), Math.round(ay0), Math.round(ax1), Math.round(ay1), 0.0f, 1.0f, 1.0f, 0.0f);
    }
}
