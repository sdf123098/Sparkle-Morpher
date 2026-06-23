package com.micaftic.morpher.core.gpu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public final class Pie {
    public static final float tau = (float) (Math.PI * 2.0);

    public static void draw(GuiGraphics graphics, float centerX, float centerY, float innerRadius, float outerRadius, float startAngle, float endAngle, int rgba) {
        draw(graphics, centerX, centerY, innerRadius, outerRadius, startAngle, endAngle, rgba, 1.0f);
    }

    public static void draw(GuiGraphics graphics, float centerX, float centerY, float innerRadius, float outerRadius, float startAngle, float endAngle, int rgba, float feather) {
        // Always render with the standard position-color pipeline. The previous custom GLSL
        // shader path rendered incorrectly on a number of drivers (AMD desktop, GL ES
        // translation layers used by mobile launchers, etc.), so we use portable rendering
        // that behaves identically everywhere.
        drawFallback(graphics, centerX, centerY, innerRadius, outerRadius, startAngle, endAngle, rgba);
    }

    /**
     * Renders a pie/ring segment using the standard position-color pipeline. Portable across all
     * drivers (desktop GL, GL ES translation layers, etc.).
     */
    private static void drawFallback(GuiGraphics graphics, float centerX, float centerY, float innerRadius, float outerRadius, float startAngle, float endAngle, int rgba) {
        float inner = Math.max(0.0f, innerRadius);
        float span = endAngle - startAngle;
        int steps = Mth.clamp(Math.round(Math.abs(span) / 0.08f), 2, 96);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f pose = graphics.pose().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= steps; i++) {
            float angle = startAngle + (span * i) / steps;
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);
            builder.addVertex(pose, centerX + outerRadius * cos, centerY + outerRadius * sin, 0.0f).setColor(rgba);
            builder.addVertex(pose, centerX + inner * cos, centerY + inner * sin, 0.0f).setColor(rgba);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.disableBlend();
    }
}
