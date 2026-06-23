package com.micaftic.morpher.core.gpu;

import com.mojang.blaze3d.opengl.GlStateManager;

import com.mojang.blaze3d.vertex.BufferUploader;
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
}
