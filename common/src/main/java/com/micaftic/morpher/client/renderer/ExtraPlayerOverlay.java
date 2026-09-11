package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.config.ExtraPlayerRenderConfig;
import com.micaftic.morpher.config.HudLayoutConfig;
import com.micaftic.morpher.client.gui.HudLayoutScreen;
import com.micaftic.morpher.client.renderer.modernhud.ModernHudRenderer;
import com.micaftic.morpher.client.renderer.preview.PaperDollActivity;
import com.micaftic.morpher.client.renderer.preview.PaperDollLayout;
import com.micaftic.morpher.client.renderer.preview.PaperDollVisibilityPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import com.micaftic.morpher.core.api.client.HudOverlay;

public class ExtraPlayerOverlay implements HudOverlay {
    @Override
    public void render(GuiGraphics guiGraphics, Font font, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft;
        LocalPlayer localPlayer;
        if ((localPlayer = (minecraft = Minecraft.getInstance()).player) == null || minecraft.screen instanceof HudLayoutScreen) {
            return;
        }
        if (ExtraPlayerRenderConfig.ENABLE_MODERN_HUD_RENDER.get()
                && ModernHudRenderer.render(guiGraphics, localPlayer, partialTick, screenWidth, screenHeight)) {
            return;
        }
        if (ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER.get()) return;
        if (!shouldRenderClassicDoll(localPlayer, minecraft)) {
            return;
        }
        // 锚点 + 偏移解析（§22.2 anchor）：默认 TOP_LEFT + 原像素值 = 历史行为。
        HudLayoutConfig layout = ExtraPlayerRenderConfig.CLASSIC_HUD_LAYOUT;
        float scale = layout.getScale();
        int dollWidth = Math.max(1, Math.round(scale));
        int dollHeight = Math.max(1, Math.round(scale * 2.0f));
        PaperDollLayout.Position position = PaperDollLayout.resolve(
                anchor(), screenWidth, screenHeight, layout.getX(), layout.getY(), dollWidth, dollHeight);
        ModelPreviewRenderer.renderPlayerOverlay(guiGraphics, localPlayer, position.x(), position.y(), scale, layout.getYaw(), -500, partialTick);
    }

    /** 经典 HUD 小人可见性（§22.2 auto-hide / F5 hide / 动作可见性）。默认配置恒返回 true。 */
    private static boolean shouldRenderClassicDoll(LocalPlayer player, Minecraft minecraft) {
        boolean firstPersonCamera = minecraft.options.getCameraType().isFirstPerson();
        boolean inAction = PaperDollActivity.isActive(player);
        if (inAction) {
            PaperDollActivity.markActive();
        }
        PaperDollVisibilityPolicy.Inputs inputs =
                new PaperDollVisibilityPolicy.Inputs(firstPersonCamera, inAction, PaperDollActivity.idleSeconds());
        return PaperDollVisibilityPolicy.shouldRender(visibilityConfig(), inputs);
    }

    /** 配置读取容错：任一项不可读时退回默认值（= 恒显示、历史行为）。 */
    private static PaperDollVisibilityPolicy.Config visibilityConfig() {
        try {
            boolean hideInThirdPerson = ExtraPlayerRenderConfig.CLASSIC_HUD_HIDE_IN_THIRD_PERSON != null
                    && ExtraPlayerRenderConfig.CLASSIC_HUD_HIDE_IN_THIRD_PERSON.get();
            boolean showOnActionOnly = ExtraPlayerRenderConfig.CLASSIC_HUD_SHOW_ON_ACTION_ONLY != null
                    && ExtraPlayerRenderConfig.CLASSIC_HUD_SHOW_ON_ACTION_ONLY.get();
            int autoHideSeconds = ExtraPlayerRenderConfig.CLASSIC_HUD_AUTO_HIDE_IDLE_SECONDS != null
                    ? ExtraPlayerRenderConfig.CLASSIC_HUD_AUTO_HIDE_IDLE_SECONDS.get() : 0;
            return new PaperDollVisibilityPolicy.Config(hideInThirdPerson, showOnActionOnly, autoHideSeconds);
        } catch (Throwable ignored) {
            return PaperDollVisibilityPolicy.Config.alwaysVisible();
        }
    }

    /** 配置读取容错：配置未就绪时按默认 TOP_LEFT（历史行为）处理。 */
    private static PaperDollLayout.Anchor anchor() {
        try {
            return ExtraPlayerRenderConfig.CLASSIC_HUD_ANCHOR != null
                    ? ExtraPlayerRenderConfig.CLASSIC_HUD_ANCHOR.get() : PaperDollLayout.Anchor.TOP_LEFT;
        } catch (Throwable ignored) {
            return PaperDollLayout.Anchor.TOP_LEFT;
        }
    }
}
