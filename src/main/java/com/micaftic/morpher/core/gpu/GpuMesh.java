package com.micaftic.morpher.core.gpu;

import com.micaftic.morpher.util.ModelMemoryProfiler;
import com.micaftic.morpher.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class GpuMesh {
    public final long pointer;
    public final int vao;
    public final int vbo;
    public final int ibo;
    public final int boneSsbo;
    public final int vertexCount;
    public final int indexCount;
    public final int boneCount;
    public final int partMask1Start, partMask1Count;
    public final int partMask2Start, partMask2Count;
    public final int partMask3Start, partMask3Count;
    public final ByteBuffer perFrameBoneBuffer;

    private int xformVbo = 0;
    private int xformVao = 0;
    private boolean disposed = false;

    GpuMesh(long pointer, int vao, int vbo, int ibo, int boneSsbo, int vertexCount, int indexCount, int boneCount, int pm1s, int pm1c, int pm2s, int pm2c, int pm3s, int pm3c) {
        this.pointer = pointer;
        this.vao = vao;
        this.vbo = vbo;
        this.ibo = ibo;
        this.boneSsbo = boneSsbo;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.boneCount = boneCount;
        this.partMask1Start = pm1s;
        this.partMask1Count = pm1c;
        this.partMask2Start = pm2s;
        this.partMask2Count = pm2c;
        this.partMask3Start = pm3s;
        this.partMask3Count = pm3c;
        this.perFrameBoneBuffer = MemoryUtil.memAlloc(boneCount * 144).order(ByteOrder.nativeOrder());
        ModelMemoryProfiler.log("gpu-mesh-built bones=" + boneCount + " vertices=" + vertexCount + " indices=" + indexCount, null);
    }

    public int indexOffsetBytes(int renderPartMask) {
        if (renderPartMask == 0) return 0;
        if (renderPartMask == 1) return partMask1Start * Integer.BYTES;
        if (renderPartMask == 2) return partMask2Start * Integer.BYTES;
        if (renderPartMask == 3) return partMask3Start * Integer.BYTES;
        return 0;
    }

    public int indexDrawCount(int renderPartMask) {
        if (renderPartMask == 0) return indexCount;
        if (renderPartMask == 1) return partMask1Count;
        if (renderPartMask == 2) return partMask2Count;
        if (renderPartMask == 3) return partMask3Count;
        return 0;
    }

    public int xformVbo() {
        return xformVbo;
    }

    public int xformVao() {
        return xformVao;
    }

    public void ensureXformBuffers() {
        if (xformVao != 0) return;
        xformVbo = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, xformVbo);
        GL45.glBufferData(GL15.GL_ARRAY_BUFFER, (long) vertexCount * 36, GL15.GL_DYNAMIC_DRAW);
        xformVao = GL45.glGenVertexArrays();
        GL45.glBindVertexArray(xformVao);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, xformVbo);
        GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL15.GL_FLOAT, false, 36, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, 36, 12L);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 2, GL15.GL_FLOAT, false, 36, 16L);
        GL20.glEnableVertexAttribArray(3);
        GL30.glVertexAttribIPointer(3, 2, GL11.GL_SHORT, 36, 24L);
        GL20.glEnableVertexAttribArray(4);
        GL30.glVertexAttribIPointer(4, 2, GL11.GL_SHORT, 36, 28L);
        GL20.glEnableVertexAttribArray(5);
        GL20.glVertexAttribPointer(5, 3, GL11.GL_BYTE, true, 36, 32L);
        GL45.glBindVertexArray(0);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        GlStateManager._glDeleteBuffers(vbo);
        GlStateManager._glDeleteBuffers(ibo);
        GlStateManager._glDeleteBuffers(boneSsbo);
        GL45.glDeleteVertexArrays(vao);
        if (xformVbo != 0) GlStateManager._glDeleteBuffers(xformVbo);
        if (xformVao != 0) GL45.glDeleteVertexArrays(xformVao);
        if (pointer != 0) {
            GeoModel.nFreeGpuMesh(pointer);
        }
        MemoryUtil.memFree(perFrameBoneBuffer);
        ModelMemoryProfiler.log("gpu-mesh-disposed bones=" + boneCount + " vertices=" + vertexCount + " indices=" + indexCount, null);
    }
}
