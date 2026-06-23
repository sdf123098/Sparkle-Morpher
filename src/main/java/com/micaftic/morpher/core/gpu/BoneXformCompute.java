package com.micaftic.morpher.core.gpu;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.util.log.ChatLogger;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;

public final class BoneXformCompute {
    private static int program = 0;
    private static int locColor = -1;
    private static int locOverlay = -1;
    private static int locModelView = -1;
    private static boolean failed = false;

    public static synchronized boolean ensureCompiled() {
        if (program != 0) return true;
        if (failed) return false;
        RenderSystem.assertOnRenderThreadOrInit();

        try {
            int cs = ShaderUtil.compileShaderFromResource(GL43.GL_COMPUTE_SHADER, "/bone_xform.csh");
            int prog = ShaderUtil.linkProgram(cs);

            locColor = GL20.glGetUniformLocation(prog, "u_color");
            locOverlay = GL20.glGetUniformLocation(prog, "u_packedOverlay");
            locModelView = GL20.glGetUniformLocation(prog, "u_modelView");

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

    public static int locColor() {
        return locColor;
    }

    public static int locOverlay() {
        return locOverlay;
    }
    public static int locModelView() { return locModelView; } // 新增 getter

    public static int dispatchGroupCount(int vertexCount) {
        return (vertexCount + 64 - 1) / 64;
    }

}
