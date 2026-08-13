package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.config.ExtraPlayerRenderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Canvas editor for the classic HUD renderer. It deliberately has no key binding: the only
 * entry is the unified settings screen, while the classic HUD keeps free positioning and
 * continuous scale/yaw adjustment.
 */
public final class ClassicHudLayoutScreen extends Screen {
    private static final float MIN_SCALE = 8.0f;
    private static final float MAX_SCALE = 360.0f;
    private static final int HANDLE_RADIUS = 7;

    private final Screen parent;
    private int playerX;
    private int playerY;
    private float playerScale;
    private float playerYaw;
    private DragMode dragMode = DragMode.NONE;
    private double grabOffsetX;
    private double grabOffsetY;

    public ClassicHudLayoutScreen(Screen parent) {
        super(Component.translatable("gui.sparkle_morpher.classic_hud_layout.title"));
        this.parent = parent;
        this.playerX = ExtraPlayerRenderConfig.PLAYER_POS_X.get();
        this.playerY = ExtraPlayerRenderConfig.PLAYER_POS_Y.get();
        this.playerScale = ExtraPlayerRenderConfig.PLAYER_SCALE.get().floatValue();
        this.playerYaw = ExtraPlayerRenderConfig.PLAYER_YAW_OFFSET.get().floatValue();
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x66000000);
        int right = Math.round(this.playerX + this.playerScale);
        int bottom = Math.round(this.playerY + this.playerScale * 2.0f);

        graphics.fill(this.playerX, this.playerY, right, bottom, 0x28305060);
        graphics.vLine(this.playerX, this.playerY, bottom, 0xFFFF5555);
        graphics.vLine(right, this.playerY, bottom, 0xFFFF5555);
        graphics.hLine(this.playerX, right, this.playerY, 0xFFFF5555);
        graphics.hLine(this.playerX, right, bottom, 0xFFFF5555);
        graphics.fill(right - HANDLE_RADIUS, bottom - HANDLE_RADIUS,
                right + HANDLE_RADIUS, bottom + HANDLE_RADIUS, 0xFF4488FF);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);
        graphics.drawString(this.font,
                Component.translatable("gui.sparkle_morpher.classic_hud_layout.tips"),
                12, 28, 0xFFE7E2D8, false);
        graphics.drawString(this.font,
                Component.translatable("gui.sparkle_morpher.classic_hud_layout.values",
                        this.playerX, this.playerY, Math.round(this.playerScale), Math.round(this.playerYaw)),
                12, 42, 0xFFB9B3AA, false);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            ModelPreviewRenderer.renderPlayerOverlay(graphics, minecraft.player, this.playerX, this.playerY,
                    this.playerScale, this.playerYaw, -500, partialTick);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int right = Math.round(this.playerX + this.playerScale);
        int bottom = Math.round(this.playerY + this.playerScale * 2.0f);
        boolean inBox = mouseX >= this.playerX && mouseX <= right && mouseY >= this.playerY && mouseY <= bottom;
        boolean inScaleHandle = Math.abs(mouseX - right) <= HANDLE_RADIUS * 2.0
                && Math.abs(mouseY - bottom) <= HANDLE_RADIUS * 2.0;
        if (button == 0 && inScaleHandle) {
            this.dragMode = DragMode.SCALE;
            return true;
        }
        if (button == 0 && inBox) {
            this.dragMode = DragMode.MOVE;
            this.grabOffsetX = mouseX - this.playerX;
            this.grabOffsetY = mouseY - this.playerY;
            return true;
        }
        if (button == 1 && inBox) {
            this.dragMode = DragMode.YAW;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        switch (this.dragMode) {
            case MOVE -> {
                this.playerX = Mth.clamp((int) Math.round(mouseX - this.grabOffsetX), 0, Math.max(0, this.width - 1));
                this.playerY = Mth.clamp((int) Math.round(mouseY - this.grabOffsetY), 0, Math.max(0, this.height - 1));
                return true;
            }
            case SCALE -> {
                float widthScale = (float) mouseX - this.playerX;
                float heightScale = ((float) mouseY - this.playerY) * 0.5f;
                this.playerScale = Mth.clamp(Math.min(widthScale, heightScale), MIN_SCALE, MAX_SCALE);
                return true;
            }
            case YAW -> {
                this.playerYaw += (float) dragX * 2.0f;
                return true;
            }
            default -> {
                return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            }
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragMode = DragMode.NONE;
        return super.mouseReleased(mouseX, mouseY, button);
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
    public boolean charTyped(char codePoint, int modifiers) {
        if (Character.toLowerCase(codePoint) == 'r' && hasAltDown()) {
            resetLayout();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void resetLayout() {
        this.playerX = 10;
        this.playerY = 10;
        this.playerScale = 40.0f;
        this.playerYaw = 0.0f;
    }

    @Override
    public void onClose() {
        ExtraPlayerRenderConfig.PLAYER_POS_X.set(this.playerX);
        ExtraPlayerRenderConfig.PLAYER_POS_Y.set(this.playerY);
        ExtraPlayerRenderConfig.PLAYER_SCALE.set((double) this.playerScale);
        ExtraPlayerRenderConfig.PLAYER_YAW_OFFSET.set((double) this.playerYaw);
        ExtraPlayerRenderConfig.PLAYER_POS_X.save();
        ExtraPlayerRenderConfig.PLAYER_POS_Y.save();
        ExtraPlayerRenderConfig.PLAYER_SCALE.save();
        ExtraPlayerRenderConfig.PLAYER_YAW_OFFSET.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private enum DragMode {
        NONE,
        MOVE,
        SCALE,
        YAW
    }
}
