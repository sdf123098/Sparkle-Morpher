package com.micaftic.morpher.core.gpu;

import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

public final class ShaderUtil {
    private ShaderUtil() {
    }

    public static String loadResource(String path) throws IOException {
        try (InputStream in = ShaderUtil.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("resource not found: " + path);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return r.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    public static int compileShader(int glType, String src, String name) {
        int sh = GL20.glCreateShader(glType);
        GL20.glShaderSource(sh, src);
        GL20.glCompileShader(sh);
        if (GL20.glGetShaderi(sh, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(sh);
            GL20.glDeleteShader(sh);
            throw new RuntimeException("Compile failed (" + name + "): " + log);
        }
        return sh;
    }

    public static int compileShaderFromResource(int glType, String resourcePath) throws IOException {
        return compileShader(glType, loadResource(resourcePath), resourcePath);
    }

    public static int linkProgram(int... shaderIds) {
        return linkProgramWith(null, shaderIds);
    }

    public static int linkProgramWith(IntConsumer preLink, int... shaderIds) {
        int prog = GL20.glCreateProgram();
        for (int sh : shaderIds) GL20.glAttachShader(prog, sh);
        if (preLink != null) preLink.accept(prog);
        GL20.glLinkProgram(prog);
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == 0) {
            String log = GL20.glGetProgramInfoLog(prog);
            GL20.glDeleteProgram(prog);
            for (int sh : shaderIds) GL20.glDeleteShader(sh);
            throw new RuntimeException("Link failed: " + log);
        }
        for (int sh : shaderIds) {
            GL20.glDetachShader(prog, sh);
            GL20.glDeleteShader(sh);
        }
        return prog;
    }
}
