package com.micaftic.morpher.core.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.util.ResourceLifecycleStats;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Blaze3DModelMeshBuilder {
    private Blaze3DModelMeshBuilder() {
    }

    public static Blaze3DModelMesh build(GeoModel model) {
        if (model.bakedBones == null || model.bakedBones.isEmpty()) return null;
        RenderSystem.assertOnRenderThread();
        GpuDevice device = RenderSystem.getDevice();
        if (device == null) {
            GpuDebugLog.warn("Blaze3D mesh build skipped: RenderSystem device is null");
            return null;
        }

        Blaze3DMeshData meshData = buildMeshData(model);
        if (meshData == null) return null;
        GpuBuffer vertexBuffer = null;
        GpuBuffer indexBuffer = null;
        GpuBuffer boneMatrixBuffer = null;
        try {
            if (meshData.vertexCount <= 0 || meshData.indexCount <= 0 || meshData.boneCount <= 0) {
                GpuDebugLog.warn("Blaze3D mesh build failed: invalid mesh vertices={} indices={} bones={}",
                        meshData.vertexCount, meshData.indexCount, meshData.boneCount);
                return null;
            }

            vertexBuffer = device.createBuffer(
                    () -> "sparkle_morpher_model_vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    meshData.vertices
            );
            indexBuffer = device.createBuffer(
                    () -> "sparkle_morpher_model_indices",
                    GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST,
                    meshData.indices
            );
            boneMatrixBuffer = device.createBuffer(
                    () -> "sparkle_morpher_model_bone_matrices",
                    GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST,
                    (long) meshData.boneCount * Blaze3DModelMesh.BONE_MATRIX_BYTES
            );

            GpuDebugLog.info("Blaze3D mesh buffers built vertices={} indices={} bones={} pm1={}+{} pm2={}+{} pm3={}+{}",
                    meshData.vertexCount, meshData.indexCount, meshData.boneCount,
                    meshData.partMask1Start, meshData.partMask1Count,
                    meshData.partMask2Start, meshData.partMask2Count,
                    meshData.partMask3Start, meshData.partMask3Count);

            return new Blaze3DModelMesh(
                    vertexBuffer,
                    indexBuffer,
                    boneMatrixBuffer,
                    meshData.vertexCount,
                    meshData.indexCount,
                    meshData.boneCount,
                    meshData.partMask1Start,
                    meshData.partMask1Count,
                    meshData.partMask2Start,
                    meshData.partMask2Count,
                    meshData.partMask3Start,
                    meshData.partMask3Count
            );
        } catch (Throwable t) {
            closeQuietly(vertexBuffer);
            closeQuietly(indexBuffer);
            closeQuietly(boneMatrixBuffer);
            GpuDebugLog.warn("Blaze3D mesh build failed: {}: {}", t.getClass().getSimpleName(), String.valueOf(t.getMessage()));
            return null;
        } finally {
            meshData.release();
        }
    }

    private static Blaze3DMeshData buildMeshData(GeoModel model) {
        int totalQuads = 0;
        for (GeoModel.BakedBone bone : model.bakedBones) {
            for (GeoModel.BakedCube cube : bone.cubes) {
                totalQuads += cube.quads.size();
            }
        }

        int vertexCount = totalQuads * 4;
        int indexCount = totalQuads * 6;
        long vertexBytes = (long) vertexCount * Blaze3DModelMesh.VERTEX_STRIDE;
        long indexBytes = (long) indexCount * Integer.BYTES;
        ByteBuffer vertices = MemoryUtil.memAlloc(Math.toIntExact(vertexBytes)).order(ByteOrder.nativeOrder());
        ResourceLifecycleStats.onDirectBufferAllocated(null, vertexBytes);
        List<QuadRecord> quads = new ArrayList<>(totalQuads);

        for (int boneIdx = 0; boneIdx < model.bakedBones.size(); boneIdx++) {
            GeoModel.BakedBone bone = model.bakedBones.get(boneIdx);
            for (GeoModel.BakedCube cube : bone.cubes) {
                for (GeoModel.BakedQuad quad : cube.quads) {
                    int vertexOffset = vertices.position() / Blaze3DModelMesh.VERTEX_STRIDE;
                    for (int v = 0; v < 4; v++) {
                        vertices.putFloat(quad.x(v));
                        vertices.putFloat(quad.y(v));
                        vertices.putFloat(quad.z(v));
                        vertices.putFloat(quad.u(v));
                        vertices.putFloat(quad.v(v));
                        vertices.put(packSnorm8(quad.normalX));
                        vertices.put(packSnorm8(quad.normalY));
                        vertices.put(packSnorm8(quad.normalZ));
                        vertices.put((byte) 0);
                        // Vulkan 对齐：BoneId/Cullable 用 4 字节 R32_UINT（步长 32 = 4 的倍数）
                        vertices.putInt(boneIdx);
                        vertices.putInt(cube.cullable ? 1 : 0);
                    }
                    quads.add(new QuadRecord(vertexOffset, bone.partMask));
                }
            }
        }
        vertices.flip();

        quads.sort(Comparator.comparingInt(q -> q.partMask));
        ByteBuffer indices = MemoryUtil.memAlloc(Math.toIntExact(indexBytes)).order(ByteOrder.nativeOrder());
        ResourceLifecycleStats.onDirectBufferAllocated(null, indexBytes);
        Blaze3DMeshData data = new Blaze3DMeshData(vertices, indices, vertexCount, indexCount, model.bakedBones.size());
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

    private static byte packSnorm8(float value) {
        int packed = Math.round(value * 127.0f);
        if (packed < -128) packed = -128;
        if (packed > 127) packed = 127;
        return (byte) packed;
    }

    private static void closeRange(Blaze3DMeshData data, int partMask, int start, int end) {
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

    private static void closeQuietly(GpuBuffer buffer) {
        if (buffer == null) {
            return;
        }
        try {
            buffer.close();
        } catch (Throwable ignored) {
        }
    }

    private static final class Blaze3DMeshData {
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

        Blaze3DMeshData(ByteBuffer vertices, ByteBuffer indices, int vertexCount, int indexCount, int boneCount) {
            this.vertices = vertices;
            this.indices = indices;
            this.vertexCount = vertexCount;
            this.indexCount = indexCount;
            this.boneCount = boneCount;
        }

        void release() {
            if (vertices != null) {
                ResourceLifecycleStats.onDirectBufferFreed(null, (long) vertexCount * Blaze3DModelMesh.VERTEX_STRIDE);
                MemoryUtil.memFree(vertices);
            }
            if (indices != null) {
                ResourceLifecycleStats.onDirectBufferFreed(null, (long) indexCount * Integer.BYTES);
                MemoryUtil.memFree(indices);
            }
        }
    }

    private record QuadRecord(int vertexOffset, int partMask) {
    }
}
