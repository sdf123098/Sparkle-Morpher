package com.micaftic.morpher.core.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import com.micaftic.morpher.core.gui.Option;
import com.micaftic.morpher.core.gui.OptionRow;

import java.text.DecimalFormat;

public class SliderOptionRow extends OptionRow<Double> {
    private final double min;
    private final double max;
    private final double step;
    private final String suffix;
    private final DecimalFormat format;
    private boolean dragging;

    public SliderOptionRow(int x, int y, int width, int height, Option<Double> option, double min, double max, double step, String suffix) {
        super(x, y, width, height, option);
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
        this.step = Math.max(step, 0.0d);
        this.suffix = suffix == null ? "" : suffix;
        this.format = this.step >= 1.0d ? new DecimalFormat("0") : new DecimalFormat("0.0");
    }

    @Override
    protected int controlWidth() {
        return Mth.clamp(width / 2, 100, 260);
    }

    @Override
    protected void renderControl(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int cx = controlX();
        int cy = controlY();
        int cw = controlWidth();
        int ch = controlHeight();
        boolean hover = isMouseOverControl(mouseX, mouseY) || dragging;

        g.fill(cx, cy, cx + cw, cy + ch, blendBg(hover, 0x41000000));
        g.outline(cx, cy, cw, ch, 0x90FFFFFF);

        double value = option.get() == null ? min : option.get();
        double range = max - min;
        double t = range <= 0.0d ? 0.0d : Mth.clamp((value - min) / range, 0.0d, 1.0d);
        int fillW = (int) (t * (cw - 2));
        g.fill(cx + 1, cy + 1, cx + 1 + fillW, cy + ch - 1, 0x50FFFFFF);

        String text = format.format(value) + suffix;
        int tw = Minecraft.getInstance().font.width(text);
        g.text(Minecraft.getInstance().font, Component.literal(text), cx + (cw - tw) / 2, cy + (ch - 8) / 2, 0xFFFFFFFF, true);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean flag) {
        if (isMouseOverControl(event.x(), event.y())) {
            dragging = true;
            updateFromMouse(event.x());
        }
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        if (dragging) updateFromMouse(event.x());
    }

    // MC 26.x: onRelease signature changed
    public void onRelease(double mouseX, double mouseY) {
        dragging = false;
    }

    private void updateFromMouse(double mouseX) {
        int cx = controlX();
        int cw = controlWidth();
        double t = Mth.clamp((mouseX - cx - 1) / (cw - 2), 0.0d, 1.0d);
        double raw = min + t * (max - min);
        if (step > 0.0d) raw = step * Math.round(raw / step);
        option.setPending(Mth.clamp(raw, min, max));
    }
}
