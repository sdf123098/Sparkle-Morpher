package com.micaftic.morpher.core.gpu;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.util.log.ChatLogger;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.opengl.GlStateManager;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;

public final class BlurShader {
    private static int program = 0;
    private static int dummyVao = 0;
    private static int locProj = -1;
    private static int locRect = -1;
    private static int locScreenSize = -1;
    private static int locRectSize = -1;
    private static int locRadius = -1;
    private static int locCorner = -1;
    private static int locBlurRadius = -1;
    private static int locGamma = -1;
    private static int locTint = -1;
    private static int locMode = -1;
    private static int locPieCenter = -1;
    private static int locPieInner = -1;
    private static int locPieOuter = -1;
    private static int locPieStart = -1;
    private static int locPieEnd = -1;
    private static int locPieFeather = -1;
    private static boolean failed = false;

    private static int captureTextureId = 0;
    private static int captureWidth = 0;
    private static int captureHeight = 0;
    private static long lastCaptureFrame = -1;

    private BlurShader() {
    }

    public static synchronized boolean ensureCompiled() {
        if (program != 0) return true;
        if (failed) return false;
        // RenderSystem.assertOnRenderThreadOrInit();
        try {
            int vs = ShaderUtil.compileShaderFromResource(GL20.GL_VERTEX_SHADER, "/blur.vsh");
            int fs = ShaderUtil.compileShaderFromResource(GL20.GL_FRAGMENT_SHADER, "/blur.fsh");
            int prog = ShaderUtil.linkProgram(vs, fs);

            locProj = GL20.glGetUniformLocation(prog, "u_proj");
            locRect = GL20.glGetUniformLocation(prog, "u_rect");
            locScreenSize = GL20.glGetUniformLocation(prog, "u_screenSize");
            locRectSize = GL20.glGetUniformLocation(prog, "u_rectSize");
            locRadius = GL20.glGetUniformLocation(prog, "u_radius");
            locCorner = GL20.glGetUniformLocation(prog, "u_corner");
            locBlurRadius = GL20.glGetUniformLocation(prog, "u_blurRadius");
            locGamma = GL20.glGetUniformLocation(prog, "u_gamma");
            locTint = GL20.glGetUniformLocation(prog, "u_tint");
            locMode = GL20.glGetUniformLocation(prog, "u_mode");
            locPieCenter = GL20.glGetUniformLocation(prog, "u_pieCenter");
            locPieInner = GL20.glGetUniformLocation(prog, "u_pieInner");
            locPieOuter = GL20.glGetUniformLocation(prog, "u_pieOuter");
            locPieStart = GL20.glGetUniformLocation(prog, "u_pieStart");
            locPieEnd = GL20.glGetUniformLocation(prog, "u_pieEnd");
            locPieFeather = GL20.glGetUniformLocation(prog, "u_pieFeather");

            int locScreen = GL20.glGetUniformLocation(prog, "u_screen");
            GL20.glUseProgram(prog);
            if (locScreen >= 0) GL20.glUniform1i(locScreen, 0);
            GL20.glUseProgram(0);

            dummyVao = GL30.glGenVertexArrays();
            program = prog;
            return true;
        } catch (Throwable t) {
            ChatLogger.INSTANCE.logFormatted("Failed to compile shader program, please check the log");
            YesSteveModel.LOGGER.error("Failed to compile shader program.", t);
            failed = true;
            return false;
        }
    }

    public static int program() {
        return program;
    }

    public static int dummyVao() {
        return dummyVao;
    }

    public static int locProj() {
        return locProj;
    }

    public static int locRect() {
        return locRect;
    }

    public static int locScreenSize() {
        return locScreenSize;
    }

    public static int locRectSize() {
        return locRectSize;
    }

    public static int locRadius() {
        return locRadius;
    }

    public static int locCorner() {
        return locCorner;
    }

    public static int locBlurRadius() {
        return locBlurRadius;
    }

    public static int locGamma() {
        return locGamma;
    }

    public static int locTint() {
        return locTint;
    }

    public static int locMode() {
        return locMode;
    }

    public static int locPieCenter() {
        return locPieCenter;
    }

    public static int locPieInner() {
        return locPieInner;
    }

    public static int locPieOuter() {
        return locPieOuter;
    }

    public static int locPieStart() {
        return locPieStart;
    }

    public static int locPieEnd() {
        return locPieEnd;
    }

    public static int locPieFeather() {
        return locPieFeather;
    }

    public static int captureTextureId() {
        return captureTextureId;
    }

    public static int captureWidth() {
        return captureWidth;
    }

    public static int captureHeight() {
        return captureHeight;
    }

    public static void captureScreen(long frameKey) {
        if (frameKey == lastCaptureFrame && frameKey >= 0) return;
        lastCaptureFrame = frameKey;
        // MC 26.x: RenderTarget moved to com.mojang.blaze3d.pipeline, field names changed
        Object main = Minecraft.getInstance().getMainRenderTarget();
        /* int w = main.viewWidth;
        int h = main.viewHeight;
        ensureCaptureTexture(w, h);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(captureTextureId);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId); */
    }

    private static void ensureCaptureTexture(int w, int h) {
        if (captureTextureId != 0 && captureWidth == w && captureHeight == h) return;
        if (captureTextureId != 0) GL11.glDeleteTextures(captureTextureId);
        captureTextureId = GL11.glGenTextures();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(captureTextureId);

        int mipLevels = 1;
        int mw = w;
        int mh = h;
        while (mw > 1 || mh > 1) {
            mw = Math.max(1, mw / 2);
            mh = Math.max(1, mh / 2);
            mipLevels++;
        }

        int lw = w;
        int lh = h;
        for (int i = 0; i < mipLevels; i++) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, i, GL11.GL_RGBA8, lw, lh, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            lw = Math.max(1, lw / 2);
            lh = Math.max(1, lh / 2);
        }

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, mipLevels - 1);

        captureWidth = w;
        captureHeight = h;
    }
}
