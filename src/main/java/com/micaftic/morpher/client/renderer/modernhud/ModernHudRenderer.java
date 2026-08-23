package com.micaftic.morpher.client.renderer.modernhud;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.config.ExtraPlayerRenderConfig;
import com.micaftic.morpher.config.HudLayoutConfig;
import com.micaftic.morpher.client.model.HandLocatorProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Vector3f;

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
        PlayerPoseSnapshot snapshot = null; // ModernHudPoseStore.consume();
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
        composite(graphics, instance, x, y);
        renderHandItems(graphics, player, snapshot, instance, x, y, scale, yawOffset);
        long count = submittedCount.incrementAndGet();
        if (count % 600 == 1) {
            YesSteveModel.LOGGER.info("[MODERN-HUD] submitted {} times (world eval={}, hud consume={})",
                    count, ModernHudPoseStore.worldEvalCount(), ModernHudPoseStore.hudConsumeCount());
        }
        return true;
    }

    /** Submit hand items to the same modern retained GUI stream as the body composite. */
    private static void renderHandItems(GuiGraphicsExtractor graphics, LocalPlayer player,
                                        PlayerPoseSnapshot snapshot, ModernHudRenderInstance instance,
                                        float posX, float posY, float scale, float yawOffset) {
        PlayerCapability capability = PlayerCapability.get(player).orElse(null);
        if (capability == null || capability.getModelAssembly() == null) {
            return;
        }
        HandLocatorProfile profile = capability.getModelAssembly().getAnimationBundle().getHandLocatorProfile();
        float originX = instance.modelOriginX(posX, scale);
        float originY = instance.modelOriginY(posY, scale);
        HumanoidArm mainArm = player.getMainArm();
        renderHandItem(graphics, player, snapshot.model(), profile, mainArm,
                player.getMainHandItem(), originX, originY, scale, yawOffset);
        renderHandItem(graphics, player, snapshot.model(), profile, mainArm.getOpposite(),
                player.getOffhandItem(), originX, originY, scale, yawOffset);
    }

    private static void renderHandItem(GuiGraphicsExtractor graphics, LocalPlayer player,
                                       com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel model,
                                       HandLocatorProfile profile, HumanoidArm arm, ItemStack stack,
                                       float originX, float originY, float scale, float yawOffset) {
        if (stack.isEmpty()) {
            return;
        }
        Vector3f point = ModernHudHandItemLayout.locate(model, profile, arm,
                originX, originY, scale, yawOffset);
        Matrix3x2f inverse = new Matrix3x2f(graphics.pose());
        if (Math.abs(inverse.determinant()) <= 1.0e-6f) {
            inverse.identity();
        } else {
            inverse.invert();
        }
        float localX = inverse.m00() * point.x + inverse.m01() * point.y + inverse.m20();
        float localY = inverse.m10() * point.x + inverse.m11() * point.y + inverse.m21();
        graphics.item(player, stack, Math.round(localX - 8.0f), Math.round(localY - 8.0f), 0);
    }

    /**
     * 把独立 HUD FBO 透明合成到 GUI 对应矩形。26.2 用 GuiGraphicsExtractor.blit(GpuTextureView, ...)
     * 走 GUI_TEXTURED 混合管线；UV 沿用 1.21.1 的约定（FBO 纹理 v=0 在矩形底部，即 v0=1/v1=0），
     * 若游戏内发现上下颠倒，把 (v0, v1) 换成 (0, 1) 即可。
     */
    private static void composite(GuiGraphicsExtractor graphics, ModernHudRenderInstance instance, float posX, float posY) {
        GpuTextureView view = instance.fboColorView();
        if (view == null) {
            return;
        }
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        int x0 = Math.round(posX - FBO_PADDING);
        int y0 = Math.round(posY - FBO_PADDING);
        int x1 = x0 + instance.fboLogicalWidth();
        int y1 = y0 + instance.fboLogicalHeight();
        graphics.blit(view, sampler, x0, y0, x1, y1, 0.0f, 1.0f, 1.0f, 0.0f);
    }
}
