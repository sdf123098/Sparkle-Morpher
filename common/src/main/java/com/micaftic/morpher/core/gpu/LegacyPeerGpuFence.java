package com.micaftic.morpher.core.gpu;

/** 1.21.1 graphics adapter; all raw GL references stay inside the existing GL boundary. */
public final class LegacyPeerGpuFence implements AutoCloseable {
    private final long fence;
    public LegacyPeerGpuFence() {
        if(!org.lwjgl.opengl.GL.getCapabilities().OpenGL32)throw new IllegalStateException("GPU fence unavailable");
        fence=org.lwjgl.opengl.GL32.glFenceSync(org.lwjgl.opengl.GL32.GL_SYNC_GPU_COMMANDS_COMPLETE,0);
        if(fence==0L)throw new IllegalStateException("GPU fence creation failed");
    }
    public boolean complete() {
        int state=org.lwjgl.opengl.GL32.glClientWaitSync(fence,0,0L);
        if(state==org.lwjgl.opengl.GL32.GL_WAIT_FAILED)throw new IllegalStateException("GPU fence wait failed");
        return state==org.lwjgl.opengl.GL32.GL_ALREADY_SIGNALED||state==org.lwjgl.opengl.GL32.GL_CONDITION_SATISFIED;
    }
    @Override public void close(){org.lwjgl.opengl.GL32.glDeleteSync(fence);}
}
