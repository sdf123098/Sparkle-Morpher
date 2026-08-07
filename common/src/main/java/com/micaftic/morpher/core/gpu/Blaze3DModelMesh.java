package com.micaftic.morpher.core.gpu;

import com.micaftic.morpher.util.ModelMemoryProfiler;
import com.micaftic.morpher.util.ResourceLifecycleStats;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Blaze3D-owned model mesh resources for Minecraft's modern graphics backend.
 * This deliberately does not expose OpenGL object ids, so it can be used when
 * Minecraft runs through Vulkan.
 */
public final class Blaze3DModelMesh implements AutoCloseable {
    static final int BONE_MATRIX_BYTES = 144;
    static final int BONE_MATRIX_TEXELS = 9;
    static final int VERTEX_STRIDE = 27;

    public final GpuBuffer vertexBuffer;
    public final GpuBuffer indexBuffer;
    public final GpuBuffer boneMatrixBuffer;
    public final ByteBuffer perFrameBoneBuffer;
    public final int vertexCount;
    public final int indexCount;
    public final int boneCount;
    public final int partMask1Start, partMask1Count;
    public final int partMask2Start, partMask2Count;
    public final int partMask3Start, partMask3Count;
    public final long estimatedBytes;

    private boolean closed;

    Blaze3DModelMesh(
            GpuBuffer vertexBuffer,
            GpuBuffer indexBuffer,
            GpuBuffer boneMatrixBuffer,
            int vertexCount,
            int indexCount,
            int boneCount,
            int pm1s,
            int pm1c,
            int pm2s,
            int pm2c,
            int pm3s,
            int pm3c
    ) {
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.boneMatrixBuffer = boneMatrixBuffer;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.boneCount = boneCount;
        this.partMask1Start = pm1s;
        this.partMask1Count = pm1c;
        this.partMask2Start = pm2s;
        this.partMask2Count = pm2c;
        this.partMask3Start = pm3s;
        this.partMask3Count = pm3c;
        this.estimatedBytes = estimateBytes(vertexCount, indexCount, boneCount);
        this.perFrameBoneBuffer = MemoryUtil.memAlloc(boneCount * BONE_MATRIX_BYTES).order(ByteOrder.nativeOrder());
        ResourceLifecycleStats.onDirectBufferAllocated(null, (long) boneCount * BONE_MATRIX_BYTES);
        ResourceLifecycleStats.onGpuMeshCreated(null, this.estimatedBytes);
        ModelMemoryProfiler.log("blaze3d-mesh-built bones=" + boneCount + " vertices=" + vertexCount + " indices=" + indexCount, null);
    }

    private static long estimateBytes(int vertexCount, int indexCount, int boneCount) {
        long vertexBytes = (long) vertexCount * VERTEX_STRIDE;
        long indexBytes = (long) indexCount * Integer.BYTES;
        long boneBytes = (long) boneCount * BONE_MATRIX_BYTES;
        long perFrameBoneBytes = (long) boneCount * BONE_MATRIX_BYTES;
        return vertexBytes + indexBytes + boneBytes + perFrameBoneBytes;
    }

    public GpuBufferSlice vertexSlice() {
        return vertexBuffer.slice();
    }

    public GpuBufferSlice indexSlice() {
        return indexBuffer.slice();
    }

    public GpuBufferSlice boneMatrixSlice() {
        return boneMatrixBuffer.slice();
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

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        vertexBuffer.close();
        indexBuffer.close();
        boneMatrixBuffer.close();
        MemoryUtil.memFree(perFrameBoneBuffer);
        ResourceLifecycleStats.onDirectBufferFreed(null, (long) boneCount * BONE_MATRIX_BYTES);
        ResourceLifecycleStats.onGpuMeshDisposed(null, this.estimatedBytes);
        ModelMemoryProfiler.log("blaze3d-mesh-disposed bones=" + boneCount + " vertices=" + vertexCount + " indices=" + indexCount, null);
    }
}
