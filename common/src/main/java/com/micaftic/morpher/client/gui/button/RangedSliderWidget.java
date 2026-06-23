package com.micaftic.morpher.client.gui.button;

import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.text.DecimalFormat;

public class RangedSliderWidget extends AbstractSliderButton {
    protected static final ResourceLocation SLIDER_LOCATION = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/slider.png");

    protected Component prefix;
    protected Component suffix;

    protected double minValue;
    protected double maxValue;
    protected double stepSize;
    protected boolean drawString;
    private boolean canChangeValue;

    private final DecimalFormat format;

    public RangedSliderWidget(int x, int y, int width, int height, Component prefix, Component suffix, double minValue, double maxValue, double currentValue, double stepSize, int precision, boolean drawString) {
        super(x, y, width, height, Component.empty(), 0D);
        this.prefix = prefix;
        this.suffix = suffix;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.stepSize = Math.abs(stepSize);
        this.value = this.snapToNearest((currentValue - minValue) / (maxValue - minValue));
        this.drawString = drawString;

        if (stepSize == 0D) {
            precision = Math.min(precision, 4);
            StringBuilder builder = new StringBuilder("0");
            if (precision > 0) builder.append('.');
            while (precision-- > 0) builder.append('0');
            this.format = new DecimalFormat(builder.toString());
        } else if (Mth.equal(this.stepSize, Math.floor(this.stepSize))) {
            this.format = new DecimalFormat("0");
        } else {
            this.format = new DecimalFormat(Double.toString(this.stepSize).replaceAll("\\d", "0"));
        }

        this.updateMessage();
    }

    public RangedSliderWidget(int x, int y, int width, int height, Component prefix, Component suffix, double minValue, double maxValue, double currentValue, boolean drawString) {
        this(x, y, width, height, prefix, suffix, minValue, maxValue, currentValue, 1D, 0, drawString);
    }

    public double getValue() {
        return this.value * (maxValue - minValue) + minValue;
    }

    public void setValue(double value) {
        double oldValue = this.value;
        this.value = this.snapToNearest((value - this.minValue) / (this.maxValue - this.minValue));
        if (!Mth.equal(oldValue, this.value)) this.applyValue();
        this.updateMessage();
    }

    public String getValueString() {
        return this.format.format(this.getValue());
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.setValueFromMouse(mouseX);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        super.onDrag(mouseX, mouseY, dragX, dragY);
        this.setValueFromMouse(mouseX);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            this.canChangeValue = false;
        } else {
            InputType inputType = Minecraft.getInstance().getLastInputType();
            if (inputType == InputType.MOUSE || inputType == InputType.KEYBOARD_TAB) {
                this.canChangeValue = true;
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean flag = keyCode == GLFW.GLFW_KEY_LEFT;
        if (flag || keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (this.minValue > this.maxValue) flag = !flag;
            float f = flag ? -1F : 1F;
            if (stepSize <= 0D) this.setSliderValue(this.value + (f / (this.width - 8)));
            else this.setValue(this.getValue() + f * this.stepSize);
            return true;
        }
        return false;
    }

    private void setValueFromMouse(double mouseX) {
        this.setSliderValue((mouseX - (this.getX() + 4)) / (this.width - 8));
    }

    private void setSliderValue(double value) {
        double oldValue = this.value;
        this.value = this.snapToNearest(value);
        if (!Mth.equal(oldValue, this.value)) this.applyValue();
        this.updateMessage();
    }

    private double snapToNearest(double value) {
        if (stepSize <= 0D) return Mth.clamp(value, 0D, 1D);
        value = Mth.lerp(Mth.clamp(value, 0D, 1D), this.minValue, this.maxValue);
        value = (stepSize * Math.round(value / stepSize));
        if (this.minValue > this.maxValue) value = Mth.clamp(value, this.maxValue, this.minValue);
        else value = Mth.clamp(value, this.minValue, this.maxValue);
        return Mth.map(value, this.minValue, this.maxValue, 0D, 1D);
    }

    @Override
    protected void updateMessage() {
        if (this.drawString) this.setMessage(Component.literal("").append(prefix).append(this.getValueString()).append(suffix));
        else this.setMessage(Component.empty());
    }

    @Override
    protected void applyValue() {}

    protected int getTextureY() {
        int i = this.isFocused() && !this.canChangeValue ? 1 : 0;
        return i * 20;
    }

    protected int getHandleTextureY() {
        int i = !this.isHovered() && !this.canChangeValue ? 2 : 3;
        return i * 20;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        final Minecraft mc = Minecraft.getInstance();

        blitWithBorder(guiGraphics, SLIDER_LOCATION, this.getX(), this.getY(), 0, getTextureY(), this.width, this.height, 200, 20, 2, 3, 2, 2);

        int handleX = this.getX() + (int)(this.value * (double)(this.width - 8));
        blitWithBorder(guiGraphics, SLIDER_LOCATION, handleX, this.getY(), 0, getHandleTextureY(), 8, this.height, 200, 20, 2, 3, 2, 2);

        int color = this.active ? 16777215 : 10526880;
        renderScrollingString(guiGraphics, mc.font, 2, color | Mth.ceil(this.alpha * 255.0F) << 24);
    }

    //https://github.com/MinecraftForge/MinecraftForge/blob/26.1.2/src/main/java/net/minecraftforge/client/extensions/IForgeGuiGraphicsExtractor.java#L71
    protected void blitWithBorder(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int u, int v, int width, int height, int textureWidth, int textureHeight, int topBorder, int bottomBorder, int leftBorder, int rightBorder) {
        int fillerWidth = textureWidth - leftBorder - rightBorder;
        int fillerHeight = textureHeight - topBorder - bottomBorder;
        int canvasWidth = width - leftBorder - rightBorder;
        int canvasHeight = height - topBorder - bottomBorder;
        int xPasses = canvasWidth / fillerWidth;
        int remainderWidth = canvasWidth % fillerWidth;
        int yPasses = canvasHeight / fillerHeight;
        int remainderHeight = canvasHeight % fillerHeight;

        guiGraphics.blit(texture, x, y, u, v, leftBorder, topBorder);
        guiGraphics.blit(texture, x + leftBorder + canvasWidth, y, u + leftBorder + fillerWidth, v, rightBorder, topBorder);
        guiGraphics.blit(texture, x, y + topBorder + canvasHeight, u, v + topBorder + fillerHeight, leftBorder, bottomBorder);
        guiGraphics.blit(texture, x + leftBorder + canvasWidth, y + topBorder + canvasHeight, u + leftBorder + fillerWidth, v + topBorder + fillerHeight, rightBorder, bottomBorder);

        for (int i = 0; i < xPasses + (remainderWidth > 0 ? 1 : 0); i++) {
            int drawWidth = (i == xPasses ? remainderWidth : fillerWidth);
            guiGraphics.blit(texture, x + leftBorder + (i * fillerWidth), y, u + leftBorder, v, drawWidth, topBorder);
            guiGraphics.blit(texture, x + leftBorder + (i * fillerWidth), y + topBorder + canvasHeight, u + leftBorder, v + topBorder + fillerHeight, drawWidth, bottomBorder);

            for (int j = 0; j < yPasses + (remainderHeight > 0 ? 1 : 0); j++) {
                int drawHeight = (j == yPasses ? remainderHeight : fillerHeight);
                guiGraphics.blit(texture, x + leftBorder + (i * fillerWidth), y + topBorder + (j * fillerHeight), u + leftBorder, v + topBorder, drawWidth, drawHeight);
            }
        }

        for (int j = 0; j < yPasses + (remainderHeight > 0 ? 1 : 0); j++) {
            int drawHeight = (j == yPasses ? remainderHeight : fillerHeight);
            guiGraphics.blit(texture, x, y + topBorder + (j * fillerHeight), u, v + topBorder, leftBorder, drawHeight);
            guiGraphics.blit(texture, x + leftBorder + canvasWidth, y + topBorder + (j * fillerHeight), u + leftBorder + fillerWidth, v + topBorder, rightBorder, drawHeight);
        }
    }
}