package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.config.ExtraPlayerRenderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;


public class ExtraPlayerRenderScreen extends Screen {

    private static final int TEXT_COLOR = 0xFFFFFFFF;

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

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor guiGraphics = extractor;
        int boxLeft = this.mouseStartX;
        int boxTop = this.mouseStartY;
        int boxRight = (int) (boxLeft + (this.rotationX));
        int boxBottom = (int) (boxTop + (this.rotationX * 2.0f));
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(0.0f, 0.0f);
        guiGraphics.verticalLine((this.width / 2) - 1, -2, this.height + 2, -1610612737);
        guiGraphics.horizontalLine(-2, this.width + 2, (this.height / 2) - 1, -1610612737);
        guiGraphics.verticalLine(10, -2, this.height + 2, -1610612737);
        guiGraphics.verticalLine(this.width - 10, -2, this.height + 2, -1610612737);
        guiGraphics.horizontalLine(-2, this.width + 2, 10, -1610612737);
        guiGraphics.horizontalLine(-2, this.width + 2, this.height - 10, -1610612737);
        guiGraphics.verticalLine(boxLeft, boxTop, boxBottom, -65536);
        guiGraphics.verticalLine(boxRight, boxTop, boxBottom, -65536);
        guiGraphics.horizontalLine(boxLeft, boxRight, boxTop, -65536);
        guiGraphics.horizontalLine(boxLeft, boxRight, boxBottom, -65536);
        guiGraphics.fillGradient(boxLeft, boxTop, boxRight, boxBottom, 1342177279, 1342177279);
        guiGraphics.fillGradient(boxLeft - this.offsetX, boxTop - this.offsetX, boxLeft + this.offsetX, boxTop + this.offsetX, -16711777, -16711777);
        guiGraphics.fillGradient(boxRight - this.offsetX, boxBottom - this.offsetX, boxRight + this.offsetX, boxBottom + this.offsetX, -16777057, -16777057);
        int tipY = 15;
        for (FormattedCharSequence formattedCharSequence : this.font.split(Component.translatable("gui.sparkle_morpher.extra_player_render.tips"), 500)) {
            guiGraphics.text(this.font, formattedCharSequence, (this.width - 15) - this.font.width(formattedCharSequence), tipY, TEXT_COLOR);
            tipY += 10;
        }
        guiGraphics.pose().popMatrix();
        if (Minecraft.getInstance().player != null && !ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER.get().booleanValue()) {
            ModelPreviewRenderer.renderPlayerOverlay(guiGraphics, Minecraft.getInstance().player, this.mouseStartX, this.mouseStartY, this.rotationX, this.rotationY, -500, partialTick, false);
        }
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        boolean inLeftHandleX = ((double) (this.mouseStartX - this.offsetX)) < event.x() && event.x() < ((double) (this.mouseStartX + this.offsetX));
        boolean inLeftHandleY = ((double) (this.mouseStartY - this.offsetX)) < event.y() && event.y() < ((double) (this.mouseStartY + this.offsetX));
        if (event.button() == 0 && inLeftHandleX && inLeftHandleY) {
            this.isDragging = true;
        }
        int rightHandleX = (int) (this.mouseStartX + (this.rotationX));
        int rightHandleY = (int) (this.mouseStartY + (this.rotationX * 2.0f));
        boolean inRightHandleX = ((double) (rightHandleX - this.offsetX)) < event.x() && event.x() < ((double) (rightHandleX + this.offsetX));
        boolean inRightHandleY = ((double) (rightHandleY - this.offsetX)) < event.y() && event.y() < ((double) (rightHandleY + this.offsetX));
        if (event.button() == 0 && inRightHandleX && inRightHandleY) {
            this.isRightDragging = true;
        }
        return super.mouseClicked(event, flag);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.isDragging = false;
        this.isRightDragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (this.isRightDragging) {
            this.rotationX = Mth.clamp((float) Math.min(mouseX - this.mouseStartX, (mouseY - this.mouseStartY) / 2.0d), 8.0f, 360.0f);
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
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (Character.toLowerCase(event.codepoint()) == RESET_KEY && Minecraft.getInstance().hasAltDown()) {
            resetTransform();
        }
        return super.charTyped(event);
    }

    private void resetTransform() {
        this.mouseStartX = 10;
        this.mouseStartY = 10;
        this.rotationX = 40.0f;
        this.rotationY = 5.0f;
    }

    public void onClose() {
        ExtraPlayerRenderConfig.PLAYER_POS_X.set(Integer.valueOf(this.mouseStartX));
        ExtraPlayerRenderConfig.PLAYER_POS_Y.set(Integer.valueOf(this.mouseStartY));
        ExtraPlayerRenderConfig.PLAYER_SCALE.set(Double.valueOf(this.rotationX));
        ExtraPlayerRenderConfig.PLAYER_YAW_OFFSET.set(Double.valueOf(this.rotationY));
        ExtraPlayerRenderConfig.PLAYER_YAW_OFFSET.save();
        super.onClose();
    }
}
