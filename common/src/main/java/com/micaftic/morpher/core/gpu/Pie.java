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
        if (!SmGraphicsBackendDetector.isRawOpenGlAllowed() || !PieShader.ensureCompiled()) {
            drawFallback(graphics, centerX, centerY, innerRadius, outerRadius, startAngle, endAngle, rgba);
            return;
        }

        float pad = feather + 1.0f;
        float rectX = centerX - outerRadius - pad;
        float rectY = centerY - outerRadius - pad;
        float rectW = (outerRadius + pad) * 2.0f;
        float rectH = (outerRadius + pad) * 2.0f;

        // Map GUI-scaled pixel coordinates to NDC. This inherently accounts for
        // the vanilla GUI Scale option (guiScaled range fills the framebuffer),
        // replacing the previous identity matrix that ignored scale/projection.
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mvpScratch.identity().setOrtho(0.0f, (float) mc.getWindow().getGuiScaledWidth(),
                (float) mc.getWindow().getGuiScaledHeight(), 0.0f, -1000.0f, 1000.0f);
        mvpScratch.get(mvpFloats);

        float cr = ((rgba >> 16) & 0xFF) / 255.0f;
        float cg = ((rgba >> 8) & 0xFF) / 255.0f;
        float cb = (rgba & 0xFF) / 255.0f;
        float ca = ((rgba >> 24) & 0xFF) / 255.0f;

        GlStateManager._enableBlend();
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

        GlStateManager._disableBlend();
    }

    private static void drawFallback(GuiGraphicsExtractor graphics, float centerX, float centerY, float innerRadius, float outerRadius, float startAngle, float endAngle, int rgba) {
        float inner = Math.max(0.0f, innerRadius);
        float outerSq = outerRadius * outerRadius;
        float innerSq = inner * inner;
        float span = endAngle - startAngle;
        boolean fullCircle = Math.abs(span) >= tau - 0.001f;
        float normStart = fullCircle ? 0.0f : normalize(startAngle);
        float normEnd = fullCircle ? 0.0f : normalize(endAngle);
        boolean ccw = span >= 0.0f;
        int minX = (int) Math.floor(centerX - outerRadius);
        int maxX = (int) Math.ceil(centerX + outerRadius);
        int minY = (int) Math.floor(centerY - outerRadius);
        int maxY = (int) Math.ceil(centerY + outerRadius);

        // Scanline analytic geometry: on a fixed row, coverage of a ring segment can
        // only change at intersections with the outer circle, the inner circle and
        // the two angle-boundary rays. Collect those x candidates (<= 6), sort and
        // dedupe, then a mid-point test per adjacent pair yields at most 3 fill runs
        // per row -- O(rows) instead of O(area) with atan2 per pixel. Backend-agnostic.
        float[] xs = new float[6];
        for (int y = minY; y < maxY; y++) {
            float py = y + 0.5f;
            float dy = py - centerY;
            float dySq = dy * dy;
            if (dySq > outerSq) {
                continue;
            }
            int count = 0;
            float halfOuter = (float) Math.sqrt(Math.max(0.0f, outerSq - dySq));
            xs[count++] = centerX - halfOuter;
            xs[count++] = centerX + halfOuter;
            if (innerSq > 0.0f && dySq < innerSq) {
                float halfInner = (float) Math.sqrt(Math.max(0.0f, innerSq - dySq));
                xs[count++] = centerX - halfInner;
                xs[count++] = centerX + halfInner;
            }
            if (!fullCircle) {
                xs[count++] = centerX + dy * cot(normStart);
                xs[count++] = centerX + dy * cot(normEnd);
            }
            java.util.Arrays.sort(xs, 0, count);
            int unique = 0;
            for (int i = 0; i < count; i++) {
                if (unique == 0 || xs[i] - xs[unique - 1] > 0.001f) {
                    xs[unique++] = xs[i];
                }
            }
            for (int i = 0; i + 1 < unique; i++) {
                float xm = (xs[i] + xs[i + 1]) * 0.5f;
                if (!contains(xm, py, centerX, centerY, innerSq, outerSq, normStart, normEnd, ccw, fullCircle)) {
                    continue;
                }
                int x0 = (int) Math.ceil(xs[i] - 0.5f);
                int x1 = (int) Math.floor(xs[i + 1] - 0.5f);
                if (x0 < minX) {
                    x0 = minX;
                }
                if (x1 >= maxX) {
                    x1 = maxX - 1;
                }
                if (x1 >= x0) {
                    graphics.fill(x0, y, x1 + 1, y + 1, rgba);
                }
            }
        }
    }

    private static float cot(float angle) {
        float sin = (float) Math.sin(angle);
        if (Math.abs(sin) < 1.0E-4f) {
            return sin >= 0.0f ? 1.0E7f : -1.0E7f;
        }
        return (float) Math.cos(angle) / sin;
    }

    private static boolean contains(float x, float y, float centerX, float centerY, float innerSq, float outerSq, float normStart, float normEnd, boolean ccw, boolean fullCircle) {
        float dx = x - centerX;
        float dy = y - centerY;
        float distSq = dx * dx + dy * dy;
        if (distSq > outerSq || distSq < innerSq) {
            return false;
        }
        if (fullCircle) {
            return true;
        }
        float angle = normalize((float) Math.atan2(dy, dx));
        if (ccw) {
            return normStart <= normEnd ? angle >= normStart && angle <= normEnd : angle >= normStart || angle <= normEnd;
        }
        return normEnd <= normStart ? angle <= normStart && angle >= normEnd : angle <= normStart || angle >= normEnd;
    }

    private static float normalize(float angle) {
        angle %= tau;
        return angle < 0.0f ? angle + tau : angle;
    }
}
