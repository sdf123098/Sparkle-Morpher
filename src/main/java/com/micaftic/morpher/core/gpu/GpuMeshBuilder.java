package com.micaftic.morpher.core.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.util.ResourceLifecycleStats;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class GpuMeshBuilder {
    private static final int VERTEX_STRIDE = 32;

    public static GpuMesh build(GeoModel model) {
        if (model.bakedBones == null || model.bakedBones.isEmpty()) return null;
        RenderSystem.assertOnRenderThread();
        MeshData meshData = buildJavaMesh(model);
        if (meshData == null || meshData.vertexCount <= 0 || meshData.indexCount <= 0 || meshData.boneCount <= 0) {
            return null;
        }

        try {
            int vao = GL30.glGenVertexArrays();
            int vbo = GlStateManager._glGenBuffers();
            int ibo = GlStateManager._glGenBuffers();
            int ssbo = GlStateManager._glGenBuffers();

            GL30.glBindVertexArray(vao);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, meshData.vertices, GL15.GL_STATIC_DRAW);
            GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, meshData.indices, GL15.GL_STATIC_DRAW);

            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, GL15.GL_FLOAT, false, VERTEX_STRIDE, 0L);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 2, GL15.GL_FLOAT, false, VERTEX_STRIDE, 12L);
            GL20.glEnableVertexAttribArray(2);
            GL20.glVertexAttribPointer(2, 4, GL33.GL_INT_2_10_10_10_REV, true, VERTEX_STRIDE, 20L);
            GL20.glEnableVertexAttribArray(3);
            GL30.glVertexAttribIPointer(3, 1, GL15.GL_UNSIGNED_SHORT, VERTEX_STRIDE, 24L);

            GL20.glEnableVertexAttribArray(4);
            GL20.glVertexAttribPointer(4, 1, GL11.GL_UNSIGNED_BYTE, false, VERTEX_STRIDE, 27L);

            GL30.glBindVertexArray(0);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

            GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssbo);
            GL45.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long) meshData.boneCount * 144, GL15.GL_DYNAMIC_DRAW);
            GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);

            long estimatedBytes = meshData.estimatedBytes();
            return new GpuMesh(0L, vao, vbo, ibo, ssbo, meshData.vertexCount, meshData.indexCount, meshData.boneCount,
                    meshData.partMask1Start, meshData.partMask1Count,
                    meshData.partMask2Start, meshData.partMask2Count,
                    meshData.partMask3Start, meshData.partMask3Count,
                    estimatedBytes);
        } finally {
            meshData.release();
        }
    }

    private static MeshData buildJavaMesh(GeoModel model) {
        int totalQuads = 0;
        for (GeoModel.BakedBone bone : model.bakedBones) {
            for (GeoModel.BakedCube cube : bone.cubes) {
                totalQuads += cube.quads.size();
            }
        }

        int vertexCount = totalQuads * 4;
        int indexCount = totalQuads * 6;
        int vertexBytes = vertexCount * VERTEX_STRIDE;
        ByteBuffer vertices = MemoryUtil.memAlloc(vertexBytes).order(ByteOrder.nativeOrder());
        ResourceLifecycleStats.onDirectBufferAllocated(null, vertexBytes);
        List<QuadRecord> quads = new ArrayList<>(totalQuads);

        for (int boneIdx = 0; boneIdx < model.bakedBones.size(); boneIdx++) {
            GeoModel.BakedBone bone = model.bakedBones.get(boneIdx);
            for (GeoModel.BakedCube cube : bone.cubes) {
                for (GeoModel.BakedQuad quad : cube.quads) {
                    int vertexOffset = vertices.position() / VERTEX_STRIDE;
                    int normal = packNormal(quad.normalX, quad.normalY, quad.normalZ);
                    for (int v = 0; v < 4; v++) {
                        vertices.putFloat(quad.x(v));
                        vertices.putFloat(quad.y(v));
                        vertices.putFloat(quad.z(v));
                        vertices.putFloat(quad.u(v));
                        vertices.putFloat(quad.v(v));
                        vertices.putInt(normal);
                        vertices.putShort((short) (boneIdx & 0xFFFF));
                        vertices.put((byte) (bone.partMask & 0xFF));
                        vertices.put((byte) (cube.cullable ? 1 : 0));
                        vertices.putInt(0);
                    }
                    quads.add(new QuadRecord(vertexOffset, bone.partMask));
                }
            }
        }
        vertices.flip();

        quads.sort(Comparator.comparingInt(q -> q.partMask));
        int indexBytes = indexCount * Integer.BYTES;
        ByteBuffer indices = MemoryUtil.memAlloc(indexBytes).order(ByteOrder.nativeOrder());
        ResourceLifecycleStats.onDirectBufferAllocated(null, indexBytes);
        MeshData data = new MeshData(vertices, indices, vertexCount, indexCount, model.bakedBones.size(), vertexBytes, indexBytes);
        int currentPartMask = -1;
        int rangeStart = 0;
        int indexOffset = 0;
        for (QuadRecord quad : quads) {
            if (quad.partMask != currentPartMask) {
                closeRange(data, currentPartMask, rangeStart, indexOffset);
                currentPartMask = quad.partMask;
                rangeStart = indexOffset;
            }
            int v = quad.vertexOffset;
            indices.putInt(v);
            indices.putInt(v + 1);
            indices.putInt(v + 2);
            indices.putInt(v);
            indices.putInt(v + 2);
            indices.putInt(v + 3);
            indexOffset += 6;
        }
        closeRange(data, currentPartMask, rangeStart, indexOffset);
        indices.flip();
        return data;
    }

    private static void closeRange(MeshData data, int partMask, int start, int end) {
        int count = end - start;
        if (count <= 0) return;
        switch (partMask) {
            case 1 -> {
                data.partMask1Start = start;
                data.partMask1Count = count;
            }
            case 2 -> {
                data.partMask2Start = start;
                data.partMask2Count = count;
            }
            case 3 -> {
                data.partMask3Start = start;
                data.partMask3Count = count;
            }
            default -> {
            }
        }
    }

    private static int packNormal(float x, float y, float z) {
        return packComponent(x) | (packComponent(y) << 10) | (packComponent(z) << 20);
    }

    private static int packComponent(float value) {
        int packed = Math.round(value * 511.0f);
        if (packed < -512) packed = -512;
        if (packed > 511) packed = 511;
        return packed & 0x3FF;
    }

    private record QuadRecord(int vertexOffset, int partMask) {
    }

    private static final class MeshData {
        final ByteBuffer vertices;
        final ByteBuffer indices;
        final int vertexCount;
        final int indexCount;
        final int boneCount;
        final int vertexBytes;
        final int indexBytes;
        int partMask1Start;
        int partMask1Count;
        int partMask2Start;
        int partMask2Count;
        int partMask3Start;
        int partMask3Count;

        MeshData(ByteBuffer vertices, ByteBuffer indices, int vertexCount, int indexCount, int boneCount, int vertexBytes, int indexBytes) {
            this.vertices = vertices;
            this.indices = indices;
            this.vertexCount = vertexCount;
            this.indexCount = indexCount;
            this.boneCount = boneCount;
            this.vertexBytes = vertexBytes;
            this.indexBytes = indexBytes;
        }

        long estimatedBytes() {
            return (long) vertexBytes + indexBytes + ((long) boneCount * 144L);
        }

        void release() {
            MemoryUtil.memFree(vertices);
            ResourceLifecycleStats.onDirectBufferFreed(null, vertexBytes);
            MemoryUtil.memFree(indices);
            ResourceLifecycleStats.onDirectBufferFreed(null, indexBytes);
        }
    }
}
