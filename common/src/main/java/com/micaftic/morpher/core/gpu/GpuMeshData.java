package com.micaftic.morpher.core.gpu;

import com.micaftic.morpher.util.ResourceLifecycleStats;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Platform-neutral baked mesh buffers shared by the legacy OpenGL path and the
 * upcoming Blaze3D/Vulkan-native path.
 */
final class GpuMeshData {
    static final int VERTEX_STRIDE = 32;

    final ByteBuffer vertices;
    final ByteBuffer indices;
    final int vertexCount;
    final int indexCount;
    final int boneCount;
    int partMask1Start;
    int partMask1Count;
    int partMask2Start;
    int partMask2Count;
    int partMask3Start;
    int partMask3Count;

    GpuMeshData(ByteBuffer vertices, ByteBuffer indices, int vertexCount, int indexCount, int boneCount) {
        this.vertices = vertices;
        this.indices = indices;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.boneCount = boneCount;
    }

    long vertexBytes() {
        return (long) vertexCount * VERTEX_STRIDE;
    }

    long indexBytes() {
        return (long) indexCount * Integer.BYTES;
    }

    void release() {
        if (vertices != null) {
            ResourceLifecycleStats.onDirectBufferFreed(null, vertexBytes());
            MemoryUtil.memFree(vertices);
        }
        if (indices != null) {
            ResourceLifecycleStats.onDirectBufferFreed(null, indexBytes());
            MemoryUtil.memFree(indices);
        }
    }
}
