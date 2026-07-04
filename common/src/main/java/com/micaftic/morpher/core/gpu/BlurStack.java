package com.micaftic.morpher.core.gpu;

import com.mojang.blaze3d.opengl.GlStateManager;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.micaftic.morpher.core.render.SmGraphicsBackendDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

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
            ((Executor) Minecraft.getInstance()).execute(() -> disposeAll(reason));
            return;
        }
        regions.clear();
        BlurShader.closeAll(reason);
    }

    public static boolean isEmpty() {
        return regions.isEmpty();
    }

    public static void flush(GuiGraphicsExtractor graphics) {
        if (regions.isEmpty()) return;
        if (!SmGraphicsBackendDetector.isOpenGlGuiBlurEnabled()) {
            regions.clear();
            return;
        }
        if (!BlurShader.ensureCompiled()) {
            regions.clear();
            return;
        }

        frameCounter++;
        if (!BlurShader.captureScreen(frameCounter)) {
            regions.clear();
            return;
        }

        // Map GUI-scaled pixel coordinates to NDC. This inherently accounts for
        // the vanilla GUI Scale option (guiScaled range fills the framebuffer),
        // replacing the previous identity matrix that ignored scale/projection.
        mvpScratch.identity().setOrtho(0.0f, (float) Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                (float) Minecraft.getInstance().getWindow().getGuiScaledHeight(), 0.0f, -1000.0f, 1000.0f);
        mvpScratch.get(mvpFloats);

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 771, 1, 0);
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();

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
        GlStateManager._disableBlend();

        regions.clear();
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
