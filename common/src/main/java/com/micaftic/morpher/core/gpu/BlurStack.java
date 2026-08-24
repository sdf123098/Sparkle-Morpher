package com.micaftic.morpher.core.gpu;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.List;

public final class BlurStack {
    private static final List<Region> regions = new ArrayList<>();
    private static final Matrix4f mvpScratch = new Matrix4f();
    private static final float[] mvpFloats = new float[16];
    private static long frameCounter = 0L;

    private BlurStack() {
    }

    public static void pushBlur(float x, float y, float w, float h, float cornerRadius, float blurRadius) {
        pushBlur(x, y, w, h, cornerRadius, blurRadius, 0xFFFFFFFF);
    }

    public static void pushBlur(float x, float y, float w, float h, float cornerRadius, float blurRadius, int tintRgba) {
        Region r = new Region();
        r.isPie = false;
        r.x = x;
        r.y = y;
        r.w = w;
        r.h = h;
        r.cornerRadius = cornerRadius;
        r.blurRadius = blurRadius;
        r.tintRgba = tintRgba;
        regions.add(r);
    }

    public static void pushBlurPie(float centerX, float centerY, float innerRadius, float outerRadius, float startAngle, float endAngle, float blurRadius) {
        pushBlurPie(centerX, centerY, innerRadius, outerRadius, startAngle, endAngle, blurRadius, 0xFFFFFFFF);
    }

    public static void pushBlurPie(float centerX, float centerY, float innerRadius, float outerRadius, float startAngle, float endAngle, float blurRadius, int tintRgba) {
        float pad = 1.0f;
        Region r = new Region();
        r.isPie = true;
        r.x = centerX - outerRadius - pad;
        r.y = centerY - outerRadius - pad;
        r.w = (outerRadius + pad) * 2.0f;
        r.h = (outerRadius + pad) * 2.0f;
        r.pieCenterX = centerX;
        r.pieCenterY = centerY;
        r.pieInner = innerRadius;
        r.pieOuter = outerRadius;
        r.pieStart = startAngle;
        r.pieEnd = endAngle;
        r.blurRadius = blurRadius;
        r.tintRgba = tintRgba;
        regions.add(r);
    }

    public static void popBlur() {
        if (!regions.isEmpty()) regions.remove(regions.size() - 1);
    }

    public static void clear() {
        regions.clear();
    }

    public static void disposeAll(String reason) {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(() -> disposeAll(reason));
            return;
        }
        regions.clear();
        BlurShader.closeAll(reason);
    }

    public static boolean isEmpty() {
        return regions.isEmpty();
    }

    public static void flush(GuiGraphics graphics) {
        if (regions.isEmpty()) return;
        if (!BlurShader.ensureCompiled()) {
            // The custom blur shader could not compile (common on GL ES translation layers used
            // by mobile launchers). Fall back to a solid translucent backdrop so panels stay
            // visible instead of rendering nothing.
            renderFallback(graphics);
            regions.clear();
            return;
        }

        frameCounter++;
        BlurShader.captureScreen(frameCounter);

        RenderSystem.getProjectionMatrix().mul(RenderSystem.getModelViewMatrix(), mvpScratch);
        mvpScratch.mul(graphics.pose().last().pose());
        mvpScratch.get(mvpFloats);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();

        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(BlurShader.captureTextureId());
        GlStateManager._glUseProgram(BlurShader.program());

        if (BlurShader.locProj() >= 0) GL20.glUniformMatrix4fv(BlurShader.locProj(), false, mvpFloats);
        if (BlurShader.locScreenSize() >= 0)
            GL20.glUniform2f(BlurShader.locScreenSize(), BlurShader.captureWidth(), BlurShader.captureHeight());
        if (BlurShader.locGamma() >= 0) GL20.glUniform1f(BlurShader.locGamma(), 6.0f);

        GlStateManager._glBindVertexArray(BlurShader.dummyVao());

        for (Region r : regions) {
            float tr = ((r.tintRgba >> 16) & 0xFF) / 255.0f;
            float tg = ((r.tintRgba >> 8) & 0xFF) / 255.0f;
            float tb = (r.tintRgba & 0xFF) / 255.0f;
            float ta = ((r.tintRgba >> 24) & 0xFF) / 255.0f;
            if (BlurShader.locRect() >= 0) GL20.glUniform4f(BlurShader.locRect(), r.x, r.y, r.w, r.h);
            if (BlurShader.locRectSize() >= 0) GL20.glUniform2f(BlurShader.locRectSize(), r.w, r.h);
            if (BlurShader.locBlurRadius() >= 0)
                GL20.glUniform1f(BlurShader.locBlurRadius(), Math.max(1.0f, r.blurRadius));
            if (BlurShader.locTint() >= 0) GL20.glUniform4f(BlurShader.locTint(), tr, tg, tb, ta);
            if (r.isPie) {
                if (BlurShader.locMode() >= 0) GL20.glUniform1i(BlurShader.locMode(), 1);
                if (BlurShader.locPieCenter() >= 0)
                    GL20.glUniform2f(BlurShader.locPieCenter(), r.pieCenterX, r.pieCenterY);
                if (BlurShader.locPieInner() >= 0) GL20.glUniform1f(BlurShader.locPieInner(), r.pieInner);
                if (BlurShader.locPieOuter() >= 0) GL20.glUniform1f(BlurShader.locPieOuter(), r.pieOuter);
                if (BlurShader.locPieStart() >= 0) GL20.glUniform1f(BlurShader.locPieStart(), r.pieStart);
                if (BlurShader.locPieEnd() >= 0) GL20.glUniform1f(BlurShader.locPieEnd(), r.pieEnd);
                if (BlurShader.locPieFeather() >= 0) GL20.glUniform1f(BlurShader.locPieFeather(), 1.0f);
            } else {
                if (BlurShader.locMode() >= 0) GL20.glUniform1i(BlurShader.locMode(), 0);
                if (BlurShader.locRadius() >= 0) GL20.glUniform1f(BlurShader.locRadius(), r.cornerRadius);
                if (BlurShader.locCorner() >= 0) GL20.glUniform4f(BlurShader.locCorner(), 1.0f, 1.0f, 1.0f, 1.0f);
            }
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        }

        GlStateManager._glUseProgram(0);
        BufferUploader.invalidate();
        GlStateManager._glBindVertexArray(0);
        RenderSystem.disableBlend();

        regions.clear();
    }

    /**
     * Draws each pushed region as a solid translucent backdrop without using the blur shader.
     * This guarantees that frosted-glass panels remain visible on platforms where the custom
     * GLSL shader fails to compile (e.g. mobile launchers running through a GL ES translation
     * layer). Rectangles use the standard GUI fill pipeline; pie regions reuse {@link Pie},
     * which has its own shader-free fallback.
     */
    private static void renderFallback(GuiGraphics graphics) {
        for (Region r : regions) {
            int color = fallbackColor(r.tintRgba);
            if (r.isPie) {
                Pie.draw(graphics, r.pieCenterX, r.pieCenterY, r.pieInner, r.pieOuter, r.pieStart, r.pieEnd, color);
            } else {
                int x0 = Math.round(r.x);
                int y0 = Math.round(r.y);
                int x1 = Math.round(r.x + r.w);
                int y1 = Math.round(r.y + r.h);
                if (x1 > x0 && y1 > y0) {
                    graphics.fill(x0, y0, x1, y1, color);
                }
            }
        }
    }

    private static int fallbackColor(int tintRgba) {
        // Ignore the (usually white) blur tint and use a dark translucent panel color so text
        // and widgets drawn on top stay readable.
        int alpha = (tintRgba >>> 24) & 0xFF;
        if (alpha == 0) alpha = 0xFF;
        int panelAlpha = Math.min(0xFF, Math.max(0xA0, alpha));
        return (panelAlpha << 24) | 0x101014;
    }

    private static final class Region {
        boolean isPie;
        float x;
        float y;
        float w;
        float h;
        float cornerRadius;
        float pieCenterX;
        float pieCenterY;
        float pieInner;
        float pieOuter;
        float pieStart;
        float pieEnd;
        float blurRadius;
        int tintRgba;
    }
}
