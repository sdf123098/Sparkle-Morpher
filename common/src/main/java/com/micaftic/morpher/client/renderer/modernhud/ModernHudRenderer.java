package com.micaftic.morpher.client.renderer.modernhud;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.config.ExtraPlayerRenderConfig;
import com.micaftic.morpher.config.HudLayoutConfig;
import com.micaftic.morpher.client.model.HandLocatorProfile;
import com.micaftic.morpher.core.compat.acceleratedrendering.AcceleratedRenderingCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 现代 HUD 入口（阶段 2，计划书 §5 阶段 2）。
 *
 * <p>消费世界帧共享姿态快照（{@link ModernHudPoseStore}，不触发第二次动画评估），
 * 经 {@link ModernHudRenderInstance} 提交专用 GPU 主体到独立 FBO，再合成到 GUI。
 * 成功提交后返回 true —— {@code ExtraPlayerOverlay} 据此跳过经典 HUD，保证两个
 * HUD 不重叠绘制。
 */
public final class ModernHudRenderer {
    private static final int FBO_PADDING = 8;
    private static final int Z_DEPTH = -500;
    private static final AtomicBoolean warnedRender = new AtomicBoolean(false);
    private static final AtomicLong submittedCount = new AtomicLong();
    private static final AtomicLong lastDiagFrame = new AtomicLong(Long.MIN_VALUE);

    // NeoForge 的 RenderGuiLayerEvent.Post 对每个 GUI 层都会触发一次（1.21.1 vanilla 约
    // 14-30 层/帧）；ExtraPlayerOverlay 挂在无过滤的 Post 事件上，render() 会被每帧调用多次。
    // GuiGraphics 实例由 GameRenderer.render 每帧新建且贯穿整帧所有层/屏幕绘制，用它的
    // 引用相等做帧去重：首次调用真正渲染并缓存结果，同帧后续调用直接返回缓存值。
    // 布局编辑器预览走 renderAt（显式参数），不经过本缓存。
    private static GuiGraphics dedupeGraphics;
    private static boolean dedupeResult;

    private ModernHudRenderer() {
    }

    private static void diag(String reason) {
        if (warnedRender.compareAndSet(false, true)) {
            YesSteveModel.LOGGER.warn("[MODERN-HUD] render skipped: {}", reason);
        }
    }

    /** Returns true only after the modern renderer has fully drawn and composited this frame. */
    public static boolean render(GuiGraphics graphics, LocalPlayer player, float partialTick,
                                 int screenWidth, int screenHeight) {
        // 每帧只真正执行一次（每 GUI 层 Post 事件去重）；无快照自评估/FBO 重绘成本随之降到 1 次/帧。
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
    public static boolean renderAt(GuiGraphics graphics, LocalPlayer player, float partialTick,
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

        // ImmediatelyFast / Accelerated Rendering 隔离：结束外层 GUI 批处理（flush 触发
        // ImmediatelyFast hud_batching 边界），并切到 vanilla 管线，避免现代 HUD FBO 的
        // 绘制被批处理重定向到主目标（黑底/模型逃逸，与经典 HUD 旧问题同源）。
        graphics.flush();
        boolean isolated = AcceleratedRenderingCompat.enterVanillaPipeline();
        try {
            ModernHudRenderInstance instance = ModernHudRenderInstance.getOrCreate(snapshot.modelId(), snapshot.model());
            boolean submitted = instance.tryRender(snapshot, FBO_PADDING, FBO_PADDING,
                    scale, yawOffset, partialTick);
            if (!submitted) {
                diag("instance.tryRender failed modelId=" + snapshot.modelId());
                return false;
            }
            composite(graphics, instance, x, y);
            renderHandItems(graphics, player, snapshot, instance, x, y, scale, yawOffset);
            long count = submittedCount.incrementAndGet();
            // 低频诊断：确认现代 HUD 在稳定提交（避免每次刷屏）
            if (count % 600 == 1) {
                YesSteveModel.LOGGER.info("[MODERN-HUD] submitted {} times (world eval={}, hud consume={})",
                        count, ModernHudPoseStore.worldEvalCount(), ModernHudPoseStore.hudConsumeCount());
            }
            return true;
        } finally {
            AcceleratedRenderingCompat.exitVanillaPipeline(isolated);
        }
    }

    /** Submit hand items to the modern GUI stream, alongside the body composite. */
    private static void renderHandItems(GuiGraphics graphics, LocalPlayer player,
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

    private static void renderHandItem(GuiGraphics graphics, LocalPlayer player,
                                       com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel model,
                                       HandLocatorProfile profile, HumanoidArm arm, ItemStack stack,
                                       float originX, float originY, float scale, float yawOffset) {
        if (stack.isEmpty()) {
            return;
        }
        Vector3f point = ModernHudHandItemLayout.locate(model, profile, arm,
                originX, originY, scale, yawOffset);
        graphics.renderItem(player, stack, Math.round(point.x - 8.0f), Math.round(point.y - 8.0f), 0);
    }

    /** 把独立 HUD FBO 透明合成到 GUI 对应矩形（与经典 HUD 的 blit 等价，含 alpha 合成）。 */
    private static void composite(GuiGraphics graphics, ModernHudRenderInstance instance, float posX, float posY) {
        int textureId = instance.textureId();
        if (textureId < 0) {
            return;
        }
        graphics.flush();
        RenderSystem.disableBlend();
        RenderSystem.enableBlend();
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        float x0 = posX - FBO_PADDING;
        float y0 = posY - FBO_PADDING;
        float x1 = x0 + instance.fboLogicalWidth();
        float y1 = y0 + instance.fboLogicalHeight();
        float z = Z_DEPTH;
        buffer.addVertex(x0, y1, z).setUv(0.0f, 0.0f);
        buffer.addVertex(x1, y1, z).setUv(1.0f, 0.0f);
        buffer.addVertex(x1, y0, z).setUv(1.0f, 1.0f);
        buffer.addVertex(x0, y0, z).setUv(0.0f, 1.0f);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
