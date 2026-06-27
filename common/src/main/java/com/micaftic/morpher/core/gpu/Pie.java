package com.micaftic.morpher.core.gpu;

import com.mojang.blaze3d.opengl.GlStateManager;

import com.mojang.blaze3d.vertex.BufferUploader;
import com.micaftic.morpher.core.render.SmGraphicsBackendDetector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public final class Pie {
    public static final float tau = (float) (Math.PI * 2.0);
    private static final Matrix4f mvpScratch = new Matrix4f();
    private static final float[] mvpFloats = new float[16];

    public static void draw(GuiGraphicsExtractor graphics, float centerX, float centerY, float innerRadius, float outerRadius, float startAngle, float endAngle, int rgba) {
        draw(graphics, centerX, centerY, innerRadius, outerRadius, startAngle, endAngle, rgba, 1.0f);
    }

    public static void draw(GuiGraphicsExtractor graphics, float centerX, float centerY, float innerRadius, float outerRadius, float startAngle, float endAngle, int rgba, float feather) {
        if (!SmGraphicsBackendDetector.isOpenGlGuiBlurEnabled()) {
            drawFallback(graphics, centerX, centerY, innerRadius, outerRadius, startAngle, endAngle, rgba);
            return;
        }
        if (!PieShader.ensureCompiled()) return;

        float pad = feather + 1.0f;
        float rectX = centerX - outerRadius - pad;
        float rectY = centerY - outerRadius - pad;
        float rectW = (outerRadius + pad) * 2.0f;
        float rectH = (outerRadius + pad) * 2.0f;

        new Matrix4f().mul(new Matrix4f(), mvpScratch);
        // TODO: mvpScratch.mul(graphics.poseStack.last().pose()); // poseStack removed in MC 26.x GuiGraphicsExtractor
        mvpScratch.get(mvpFloats);

        float cr = ((rgba >> 16) & 0xFF) / 255.0f;
        float cg = ((rgba >> 8) & 0xFF) / 255.0f;
        float cb = (rgba & 0xFF) / 255.0f;
        float ca = ((rgba >> 24) & 0xFF) / 255.0f;

        GlStateManager._enableBlend(0);
        GlStateManager._blendFuncSeparate(770, 771, 1, 0);
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();

        GlStateManager._glUseProgram(PieShader.program());

        if (PieShader.locProj() >= 0) GL20.glUniformMatrix4fv(PieShader.locProj(), false, mvpFloats);
        if (PieShader.locRect() >= 0) GL20.glUniform4f(PieShader.locRect(), rectX, rectY, rectW, rectH);
        if (PieShader.locCenter() >= 0) GL20.glUniform2f(PieShader.locCenter(), centerX, centerY);
        if (PieShader.locOuterRadius() >= 0) GL20.glUniform1f(PieShader.locOuterRadius(), outerRadius);
        if (PieShader.locInnerRadius() >= 0) GL20.glUniform1f(PieShader.locInnerRadius(), Math.max(0.0f, innerRadius));
        if (PieShader.locStartAngle() >= 0) GL20.glUniform1f(PieShader.locStartAngle(), startAngle);
        if (PieShader.locEndAngle() >= 0) GL20.glUniform1f(PieShader.locEndAngle(), endAngle);
        if (PieShader.locColor() >= 0) GL20.glUniform4f(PieShader.locColor(), cr, cg, cb, ca);
        if (PieShader.locFeather() >= 0) GL20.glUniform1f(PieShader.locFeather(), feather);

        GlStateManager._glBindVertexArray(PieShader.dummyVao());
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

        GlStateManager._glUseProgram(0);
        BufferUploader.invalidate();
        GlStateManager._glBindVertexArray(0);

        GlStateManager._disableBlend(0);
    }

    private static void drawFallback(GuiGraphicsExtractor graphics, float centerX, float centerY, float innerRadius, float outerRadius, float startAngle, float endAngle, int rgba) {
        float inner = Math.max(0.0f, innerRadius);
        float outerSq = outerRadius * outerRadius;
        float innerSq = inner * inner;
        int minX = (int) Math.floor(centerX - outerRadius);
        int maxX = (int) Math.ceil(centerX + outerRadius);
        int minY = (int) Math.floor(centerY - outerRadius);
        int maxY = (int) Math.ceil(centerY + outerRadius);

        for (int y = minY; y < maxY; y++) {
            int runStart = -1;
            for (int x = minX; x < maxX; x++) {
                if (contains(x + 0.5f, y + 0.5f, centerX, centerY, innerSq, outerSq, startAngle, endAngle)) {
                    if (runStart < 0) {
                        runStart = x;
                    }
                } else if (runStart >= 0) {
                    graphics.fill(runStart, y, x, y + 1, rgba);
                    runStart = -1;
                }
            }
            if (runStart >= 0) {
                graphics.fill(runStart, y, maxX, y + 1, rgba);
            }
        }
    }

    private static boolean contains(float x, float y, float centerX, float centerY, float innerSq, float outerSq, float startAngle, float endAngle) {
        float dx = x - centerX;
        float dy = y - centerY;
        float distSq = dx * dx + dy * dy;
        if (distSq > outerSq || distSq < innerSq) {
            return false;
        }
        float span = endAngle - startAngle;
        if (Math.abs(span) >= tau - 0.001f) {
            return true;
        }
        float angle = normalize((float) Math.atan2(dy, dx));
        float start = normalize(startAngle);
        float end = normalize(endAngle);
        if (span >= 0.0f) {
            return start <= end ? angle >= start && angle <= end : angle >= start || angle <= end;
        }
        return end <= start ? angle <= start && angle >= end : angle <= start || angle >= end;
    }

    private static float normalize(float angle) {
        angle %= tau;
        return angle < 0.0f ? angle + tau : angle;
    }
}
