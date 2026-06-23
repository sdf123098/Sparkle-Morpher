package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.config.ExtraPlayerRenderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;


public class ExtraPlayerRenderScreen extends Screen {

    private static final char RESET_KEY = 'r';

    private int mouseStartX;

    private int mouseStartY;

    private float rotationX;

    private float rotationY;

    private boolean isDragging;

    private boolean isRightDragging;

    private int offsetX;

    private int offsetY;

    public ExtraPlayerRenderScreen() {
        super(Component.literal("Render Config GUI"));
        this.isDragging = false;
        this.isRightDragging = false;
        this.offsetX = 5;
        this.offsetY = 1;
        this.mouseStartX = ExtraPlayerRenderConfig.PLAYER_POS_X.get().intValue();
        this.mouseStartY = ExtraPlayerRenderConfig.PLAYER_POS_Y.get().intValue();
        this.rotationX = ExtraPlayerRenderConfig.PLAYER_SCALE.get().floatValue();
        this.rotationY = ExtraPlayerRenderConfig.PLAYER_YAW_OFFSET.get().floatValue();
        if (PauseScreenButtonBuilder.isServerConnected()) {
            this.offsetX = 16;
            this.offsetY = 0;
        }
    }

    public void init() {
        clearWidgets();
        int i = -30;
        if (PauseScreenButtonBuilder.isServerConnected()) {
            addRenderableWidget(Button.builder(Component.translatable("controls.reset"), button -> {
                resetTransform();
            }).bounds((this.width / 2) - 50, this.height - 35, 100, 30).build());
            i = -60;
        }
        MutableComponent mutableComponentTranslatable = Component.translatable("gui.sparkle_morpher.hide_or_show");
        int iWidth = this.font.width(mutableComponentTranslatable) + 24;
        addRenderableWidget(Checkbox.builder(mutableComponentTranslatable, font).pos((this.width - iWidth) / 2, this.height + i).maxWidth(iWidth).selected(ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER.get().booleanValue()).onValueChange((c, v) -> {
            ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER.set(v);
            ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER.save();
        }).build());
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int boxLeft = this.mouseStartX;
        int boxTop = this.mouseStartY;
        int boxRight = (int) (boxLeft + (this.rotationX));
        int boxBottom = (int) (boxTop + (this.rotationX * 2.0f));
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0f, 0.0f, (-500.0f) - ((50.0f * this.rotationX) / 40.0f));
        guiGraphics.vLine((this.width / 2) - 1, -2, this.height + 2, -1610612737);
        guiGraphics.hLine(-2, this.width + 2, (this.height / 2) - 1, -1610612737);
        guiGraphics.vLine(10, -2, this.height + 2, -1610612737);
        guiGraphics.vLine(this.width - 10, -2, this.height + 2, -1610612737);
        guiGraphics.hLine(-2, this.width + 2, 10, -1610612737);
        guiGraphics.hLine(-2, this.width + 2, this.height - 10, -1610612737);
        guiGraphics.vLine(boxLeft, boxTop, boxBottom, -65536);
        guiGraphics.vLine(boxRight, boxTop, boxBottom, -65536);
        guiGraphics.hLine(boxLeft, boxRight, boxTop, -65536);
        guiGraphics.hLine(boxLeft, boxRight, boxBottom, -65536);
        guiGraphics.fillGradient(boxLeft, boxTop, boxRight, boxBottom, 1342177279, 1342177279);
        guiGraphics.fillGradient(boxLeft - this.offsetX, boxTop - this.offsetX, boxLeft + this.offsetX, boxTop + this.offsetX, -16711777, -16711777);
        guiGraphics.fillGradient(boxRight - this.offsetX, boxBottom - this.offsetX, boxRight + this.offsetX, boxBottom + this.offsetX, -16777057, -16777057);
        int tipY = 15;
        for (FormattedCharSequence formattedCharSequence : this.font.split(Component.translatable("gui.sparkle_morpher.extra_player_render.tips"), 500)) {
            guiGraphics.drawString(this.font, formattedCharSequence, (this.width - 15) - this.font.width(formattedCharSequence), tipY, 16777215);
            tipY += 10;
        }
        guiGraphics.pose().popPose();
        if (Minecraft.getInstance().player != null && !ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER.get().booleanValue()) {
            ModelPreviewRenderer.renderPlayerOverlay(guiGraphics, Minecraft.getInstance().player, this.mouseStartX, this.mouseStartY, this.rotationX, this.rotationY, -500, partialTick);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean inLeftHandleX = ((double) (this.mouseStartX - this.offsetX)) < mouseX && mouseX < ((double) (this.mouseStartX + this.offsetX));
        boolean inLeftHandleY = ((double) (this.mouseStartY - this.offsetX)) < mouseY && mouseY < ((double) (this.mouseStartY + this.offsetX));
        if (button == 0 && inLeftHandleX && inLeftHandleY) {
            this.isDragging = true;
        }
        int rightHandleX = (int) (this.mouseStartX + (this.rotationX));
        int rightHandleY = (int) (this.mouseStartY + (this.rotationX * 2.0f));
        boolean inRightHandleX = ((double) (rightHandleX - this.offsetX)) < mouseX && mouseX < ((double) (rightHandleX + this.offsetX));
        boolean inRightHandleY = ((double) (rightHandleY - this.offsetX)) < mouseY && mouseY < ((double) (rightHandleY + this.offsetX));
        if (button == 0 && inRightHandleX && inRightHandleY) {
            this.isRightDragging = true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isDragging = false;
        this.isRightDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isRightDragging) {
            this.rotationX = (float) Math.min(mouseX - this.mouseStartX, (mouseY - this.mouseStartY) / 2.0d);
            return true;
        }
        if (this.isDragging) {
            this.mouseStartX = (int) mouseX;
            this.mouseStartY = (int) mouseY;
            return true;
        }
        if (button == this.offsetY) {
            this.rotationY += (float) (dragX * 2.0d);
            return true;
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (Character.toLowerCase(codePoint) == RESET_KEY && hasAltDown()) {
            resetTransform();
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void resetTransform() {
        this.mouseStartX = 10;
        this.mouseStartY = 10;
        this.rotationX = 40.0f;
        this.rotationY = 0.0f;
    }

    public void onClose() {
        ExtraPlayerRenderConfig.PLAYER_POS_X.set(Integer.valueOf(this.mouseStartX));
        ExtraPlayerRenderConfig.PLAYER_POS_Y.set(Integer.valueOf(this.mouseStartY));
        ExtraPlayerRenderConfig.PLAYER_SCALE.set(Double.valueOf(this.rotationX));
        ExtraPlayerRenderConfig.PLAYER_YAW_OFFSET.set(Double.valueOf(this.rotationY));
        ExtraPlayerRenderConfig.PLAYER_POS_X.save();
        ExtraPlayerRenderConfig.PLAYER_POS_Y.save();
        ExtraPlayerRenderConfig.PLAYER_SCALE.save();
        ExtraPlayerRenderConfig.PLAYER_YAW_OFFSET.save();
        super.onClose();
    }
}
