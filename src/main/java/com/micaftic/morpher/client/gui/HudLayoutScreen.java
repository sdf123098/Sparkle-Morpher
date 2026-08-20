package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.modernhud.ModernHudRenderer;
import com.micaftic.morpher.config.HudLayoutConfig;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 通用 HUD 布局画布编辑器（26.x，GuiGraphicsExtractor 版）：拖拽移动、右下角蓝色把手/滚轮
 * 无级缩放、右键拖动旋转、Alt+R 重置。经典 HUD 与现代 HUD 各持有一份 {@link HudLayoutConfig}，
 * 本屏通过 {@link HudPreview} 回调预览对应渲染器，并把编辑结果写回自己的配置，互不干扰。
 *
 * <p>画布只有 2×scale 的方形 guide（经典 HUD framing），实际绘制以预览渲染器输出为准
 * （现代 HUD 按模型真实 AABB 自定尺寸）。
 */
public final class HudLayoutScreen extends Screen {
    private static final float MIN_SCALE = 8.0f;
    private static final float MAX_SCALE = 360.0f;
    private static final int HANDLE_RADIUS = 7;

    private final Screen parent;
    private final HudLayoutConfig config;
    private final HudPreview preview;
    private int playerX;
    private int playerY;
    private float playerScale;
    private float playerYaw;
    private DragMode dragMode = DragMode.NONE;
    private double grabOffsetX;
    private double grabOffsetY;

    /** 预览回调：把编辑中的（未保存）布局值渲染到画布；返回是否成功绘制。 */
    @FunctionalInterface
    public interface HudPreview {
        boolean render(GuiGraphicsExtractor graphics, LocalPlayer player, float partialTick,
                       int screenWidth, int screenHeight, float x, float y, float scale, float yaw);
    }

    public HudLayoutScreen(Screen parent, Component title, HudLayoutConfig config, HudPreview preview) {
        super(title);
        this.parent = parent;
        this.config = config;
        this.preview = preview;
        this.playerX = config.getX();
        this.playerY = config.getY();
        this.playerScale = config.getScale();
        this.playerYaw = config.getYaw();
    }

    /** 经典 HUD 预览：经经典路径绘制，直接落回画布坐标。 */
    public static HudPreview classicPreview() {
        return (graphics, player, partialTick, w, h, x, y, scale, yaw) -> {
            ModelPreviewRenderer.renderPlayerOverlay(graphics, player, x, y, scale, yaw, -500, partialTick, false);
            return true;
        };
    }

    /** 现代 HUD 预览：经现代 HUD 独立管线（FBO + 合成）绘制。 */
    public static HudPreview modernPreview() {
        return ModernHudRenderer::renderAt;
    }

    @Override
    protected void init() {
        int buttonY = this.height - 30;
        addRenderableWidget(Button.builder(Component.translatable("controls.reset"), button -> resetLayout())
                .bounds(this.width / 2 - 104, buttonY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(this.width / 2 + 4, buttonY, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, this.width, this.height, 0x66000000, 0x66000000);
        int right = Math.round(this.playerX + this.playerScale);
        int bottom = Math.round(this.playerY + this.playerScale * 2.0f);

        graphics.fillGradient(this.playerX, this.playerY, right, bottom, 0x28305060, 0x28305060);
        graphics.verticalLine(this.playerX, this.playerY, bottom, 0xFFFF5555);
        graphics.verticalLine(right, this.playerY, bottom, 0xFFFF5555);
        graphics.horizontalLine(this.playerX, right, this.playerY, 0xFFFF5555);
        graphics.horizontalLine(this.playerX, right, bottom, 0xFFFF5555);
        graphics.fillGradient(right - HANDLE_RADIUS, bottom - HANDLE_RADIUS,
                right + HANDLE_RADIUS, bottom + HANDLE_RADIUS, 0xFF4488FF, 0xFF4488FF);

        int titleX = (this.width - this.font.width(this.title)) / 2;
        graphics.text(this.font, this.title, titleX, 10, 0xFFFFFFFF, false);
        // tips/values 文案与经典编辑器共用（两编辑器的操作说明与数值格式完全一致）
        graphics.text(this.font, Component.translatable("gui.sparkle_morpher.classic_hud_layout.tips"),
                12, 28, 0xFFE7E2D8, false);
        graphics.text(this.font,
                Component.translatable("gui.sparkle_morpher.classic_hud_layout.values",
                        this.playerX, this.playerY, Math.round(this.playerScale), Math.round(this.playerYaw)),
                12, 42, 0xFFB9B3AA, false);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            this.preview.render(graphics, minecraft.player, partialTick,
                    this.width, this.height, this.playerX, this.playerY, this.playerScale, this.playerYaw);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        double mouseX = event.x();
        double mouseY = event.y();
        int right = Math.round(this.playerX + this.playerScale);
        int bottom = Math.round(this.playerY + this.playerScale * 2.0f);
        boolean inBox = mouseX >= this.playerX && mouseX <= right && mouseY >= this.playerY && mouseY <= bottom;
        boolean inScaleHandle = Math.abs(mouseX - right) <= HANDLE_RADIUS * 2.0
                && Math.abs(mouseY - bottom) <= HANDLE_RADIUS * 2.0;
        if (event.button() == 0 && inScaleHandle) {
            this.dragMode = DragMode.SCALE;
            return true;
        }
        if (event.button() == 0 && inBox) {
            this.dragMode = DragMode.MOVE;
            this.grabOffsetX = mouseX - this.playerX;
            this.grabOffsetY = mouseY - this.playerY;
            return true;
        }
        if (event.button() == 1 && inBox) {
            this.dragMode = DragMode.YAW;
            return true;
        }
        return super.mouseClicked(event, flag);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        switch (this.dragMode) {
            case MOVE -> {
                this.playerX = Mth.clamp((int) Math.round(event.x() - this.grabOffsetX), 0, Math.max(0, this.width - 1));
                this.playerY = Mth.clamp((int) Math.round(event.y() - this.grabOffsetY), 0, Math.max(0, this.height - 1));
                return true;
            }
            case SCALE -> {
                float widthScale = (float) event.x() - this.playerX;
                float heightScale = ((float) event.y() - this.playerY) * 0.5f;
                this.playerScale = Mth.clamp(Math.min(widthScale, heightScale), MIN_SCALE, MAX_SCALE);
                return true;
            }
            case YAW -> {
                this.playerYaw += (float) dragX * 2.0f;
                return true;
            }
            default -> {
                return super.mouseDragged(event, dragX, dragY);
            }
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragMode = DragMode.NONE;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int right = Math.round(this.playerX + this.playerScale);
        int bottom = Math.round(this.playerY + this.playerScale * 2.0f);
        if (mouseX >= this.playerX && mouseX <= right && mouseY >= this.playerY && mouseY <= bottom) {
            float step = Math.max(0.5f, this.playerScale * 0.04f);
            this.playerScale = Mth.clamp(this.playerScale + (float) scrollY * step, MIN_SCALE, MAX_SCALE);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (Character.toLowerCase(event.codepoint()) == 'r' && Minecraft.getInstance().hasAltDown()) {
            resetLayout();
            return true;
        }
        return super.charTyped(event);
    }

    private void resetLayout() {
        this.playerX = 10;
        this.playerY = 10;
        this.playerScale = 40.0f;
        this.playerYaw = 0.0f;
    }

    @Override
    public void onClose() {
        this.config.set(this.playerX, this.playerY, this.playerScale, this.playerYaw);
        this.config.save();
        InputUtil.setScreen(this.parent);
    }

    private enum DragMode {
        NONE,
        MOVE,
        SCALE,
        YAW
    }
}
