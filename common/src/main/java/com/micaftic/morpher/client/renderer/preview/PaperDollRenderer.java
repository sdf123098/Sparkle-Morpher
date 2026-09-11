package com.micaftic.morpher.client.renderer.preview;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.render.RenderContext;
import com.micaftic.morpher.client.render.RenderPass;
import com.micaftic.morpher.client.renderer.RendererManager;
import com.micaftic.morpher.config.ExtraPlayerRenderConfig;
import com.micaftic.morpher.core.config.ConfigPolicies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;

/**
 * 经典 HUD 玩家小人（Paper Doll）渲染入口（路线图 §22.1/§22.2，1.2.6 Slice D）。
 *
 * <p>从 {@code ModelPreviewRenderer} 外提：屏幕小人的绘制入口（位置/缩放/yaw 由调用方
 * 依布局配置给定）、自定义本地玩家预览排队、以及 vanilla 背包预览回退。</p>
 *
 * <p>行为等价搬迁：方法体、坐标换算与回退顺序与迁移前一致。小人的实际绘制仍复用
 * {@link GuiModelRenderer} 的 GUI 预览请求路径（排队 → PIP 回调），因此 Vulkan/OpenGL
 * 走的是同一条可移植路径，不新增 OpenGL-only 渲染器（RULE-GFX-2/3）。</p>
 *
 * <p>隔离约束（路线图 §22.3 / PaperDollIsolationTest）：本路径只做绘制，不得产生
 * gameplay / 网络副作用；对 live {@code LocalPlayer} 的朝向/姿态改动必须由
 * {@link GuiModelRenderer} 在 finally 中恢复。</p>
 */
public final class PaperDollRenderer {

    private PaperDollRenderer() {
    }

    public static void renderPlayerOverlay(GuiGraphicsExtractor guiGraphics, LocalPlayer localPlayer, double x, double y, float scale, float yawOffset, int zDepth, float partialTick) {
        renderPlayerOverlay(guiGraphics, localPlayer, x, y, scale, yawOffset, zDepth, partialTick, true);
    }

    public static void renderPlayerOverlay(GuiGraphicsExtractor guiGraphics, LocalPlayer localPlayer, double x, double y, float scale, float yawOffset, int zDepth, float partialTick, boolean clipToFrame) {
        if (guiGraphics == null || localPlayer == null || scale <= 0.0f) {
            return;
        }
        int left = Math.round((float) x);
        int top = Math.round((float) y);
        int right = Math.round((float) (x + scale));
        int bottom = Math.round((float) (y + (scale * 2.0f)));
        if (right <= left || bottom <= top) {
            return;
        }
        float centerX = (left + right) * 0.5f;
        float centerY = (top + bottom) * 0.5f;
        int renderLeft = left;
        int renderTop = top;
        int renderRight = right;
        int renderBottom = bottom;
        if (!clipToFrame) {
            Minecraft minecraft = Minecraft.getInstance();
            renderLeft = Math.min(0, left);
            renderTop = Math.min(0, top);
            renderRight = Math.max(minecraft.getWindow().getGuiScaledWidth(), right);
            renderBottom = Math.max(minecraft.getWindow().getGuiScaledHeight(), bottom);
        }
        if (renderCustomLocalPlayerPreview(guiGraphics, localPlayer, renderLeft, renderTop, renderRight, renderBottom, centerX, bottom - 2.0f, scale, 180.0f + yawOffset, partialTick, true)) {
            return;
        }
        float mouseX = centerX - ((float) Math.tan(yawOffset / 20.0f) * 40.0f);
        setExtraPlayerMode(true);
        if (clipToFrame) {
            guiGraphics.enableScissor(left, top, right, bottom);
        }
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(guiGraphics, left, top, right, bottom, Math.max(1, Math.round(scale)), mouseX, centerY, 1.0f, localPlayer);
        } finally {
            if (clipToFrame) {
                guiGraphics.disableScissor();
            }
            setExtraPlayerMode(false);
        }

    }

    public static boolean renderCustomLocalPlayerPreview(GuiGraphicsExtractor guiGraphics, LocalPlayer localPlayer, int left, int top, int right, int bottom, float originX, float originY, float scale, float yaw, float partialTick, boolean extraPlayer) {
        return renderCustomLocalPlayerPreview(guiGraphics, localPlayer, left, top, right, bottom, originX, originY, scale, yaw, partialTick, extraPlayer, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    public static boolean renderCustomLocalPlayerPreview(GuiGraphicsExtractor guiGraphics, LocalPlayer localPlayer, int left, int top, int right, int bottom, float originX, float originY, float scale, float yaw, float partialTick, boolean extraPlayer, int mouseX, int mouseY) {
        if (guiGraphics == null || localPlayer == null || right <= left || bottom <= top || scale <= 0.0f || ConfigPolicies.render().disableSelfModel()) {
            return false;
        }
        PlayerCapability capability = PlayerCapability.get(localPlayer).orElse(null);
        if (capability == null || !capability.isModelActive()) {
            return false;
        }
        if (!capability.isModelReady()) {
            capability.tickModel();
        }
        if (!capability.isModelReady()) {
            return false;
        }
        PreviewMath.MouseRotation mouseRotation = PreviewMath.mouseRotation(left, top, right, bottom, mouseX, mouseY, extraPlayer);
        GuiModelRenderer.enqueueLivingPreview(guiGraphics, left, top, right, bottom, originX, originY, scale, partialTick, capability, RendererManager.getPlayerRenderer(), false, false, yaw + mouseRotation.yaw(), mouseRotation.pitch(), extraPlayer, buildDollOptions(localPlayer));
        return true;
    }

    /**
     * 由配置构建小人选项（§22.2 head mode + 载具对齐）。配置读取全部容错：任一项不可读时
     * 退回默认值（= 历史行为），保证配置未就绪时不改变渲染。
     */
    private static GuiModelRenderer.DollOptions buildDollOptions(LocalPlayer localPlayer) {
        float headYawOffset = 0.0f;
        float headYawOffsetO = 0.0f;
        if (followPlayerHead()) {
            headYawOffset = GuiModelRenderer.extraPlayerHeadYawOffset(localPlayer);
            headYawOffsetO = GuiModelRenderer.extraPlayerHeadYawOffsetO(localPlayer);
        }
        return new GuiModelRenderer.DollOptions(headYawOffset, headYawOffsetO, alignWithVehicle());
    }

    /** 配置读取容错：配置未就绪时按默认 STRAIGHT 处理。 */
    private static boolean followPlayerHead() {
        try {
            return ExtraPlayerRenderConfig.CLASSIC_HUD_HEAD_MODE != null
                    && ExtraPlayerRenderConfig.CLASSIC_HUD_HEAD_MODE.get() == ExtraPlayerRenderConfig.HeadMode.FOLLOW;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 配置读取容错：配置未就绪时按默认「不对齐载具」处理。 */
    private static boolean alignWithVehicle() {
        try {
            return ExtraPlayerRenderConfig.CLASSIC_HUD_ALIGN_WITH_VEHICLE != null
                    && ExtraPlayerRenderConfig.CLASSIC_HUD_ALIGN_WITH_VEHICLE.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void setExtraPlayerMode(boolean extraPlayerMode) {
        if (extraPlayerMode) {
            RenderContext.enter(RenderPass.OLD_HUD);
        } else {
            RenderContext.restore(RenderPass.WORLD);
        }
    }
}
