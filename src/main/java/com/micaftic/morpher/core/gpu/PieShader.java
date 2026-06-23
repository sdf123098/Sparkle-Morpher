package com.micaftic.morpher.core.gpu;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.util.log.ChatLogger;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class PieShader {
    private static int program = 0;
    private static int dummyVao = 0;
    private static int locProj = -1;
    private static int locRect = -1;
    private static int locCenter = -1;
    private static int locOuterRadius = -1;
    private static int locInnerRadius = -1;
    private static int locStartAngle = -1;
    private static int locEndAngle = -1;
    private static int locColor = -1;
    private static int locFeather = -1;
    private static boolean failed = false;

    private PieShader() {
    }

    public static synchronized boolean ensureCompiled() {
        if (program != 0) return true;
        if (failed) return false;
        RenderSystem.assertOnRenderThreadOrInit();
        try {
            int vs = ShaderUtil.compileShaderFromResource(GL20.GL_VERTEX_SHADER, "/pie.vsh");
            int fs = ShaderUtil.compileShaderFromResource(GL20.GL_FRAGMENT_SHADER, "/pie.fsh");
            int prog = ShaderUtil.linkProgram(vs, fs);

            locProj = GL20.glGetUniformLocation(prog, "u_proj");
            locRect = GL20.glGetUniformLocation(prog, "u_rect");
            locCenter = GL20.glGetUniformLocation(prog, "u_center");
            locOuterRadius = GL20.glGetUniformLocation(prog, "u_outerRadius");
            locInnerRadius = GL20.glGetUniformLocation(prog, "u_innerRadius");
            locStartAngle = GL20.glGetUniformLocation(prog, "u_startAngle");
            locEndAngle = GL20.glGetUniformLocation(prog, "u_endAngle");
            locColor = GL20.glGetUniformLocation(prog, "u_color");
            locFeather = GL20.glGetUniformLocation(prog, "u_feather");

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

    public static int locCenter() {
        return locCenter;
    }

    public static int locOuterRadius() {
        return locOuterRadius;
    }

    public static int locInnerRadius() {
        return locInnerRadius;
    }

    public static int locStartAngle() {
        return locStartAngle;
    }

    public static int locEndAngle() {
        return locEndAngle;
    }

    public static int locColor() {
        return locColor;
    }

    public static int locFeather() {
        return locFeather;
    }
}
