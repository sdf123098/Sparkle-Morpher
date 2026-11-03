package com.micaftic.morpher.core.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.util.ResourceLifecycleStats;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class GpuMeshDataBuilder {
    private GpuMeshDataBuilder() {
    }

    static GpuMeshData build(GeoModel model) {
        int totalQuads = 0;
        for (GeoModel.BakedBone bone : model.bakedBones) {
            for (GeoModel.BakedCube cube : bone.cubes) {
                totalQuads += cube.quads.size();
            }
        }

        int vertexCount = totalQuads * 4;
        int indexCount = totalQuads * 6;
        long vertexBytes = (long) vertexCount * GpuMeshData.VERTEX_STRIDE;
        long indexBytes = (long) indexCount * Integer.BYTES;
        ByteBuffer vertices = MemoryUtil.memAlloc(Math.toIntExact(vertexBytes)).order(ByteOrder.nativeOrder());
        ResourceLifecycleStats.onDirectBufferAllocated(null, vertexBytes);
        List<QuadRecord> quads = new ArrayList<>(totalQuads);

        for (int boneIdx = 0; boneIdx < model.bakedBones.size(); boneIdx++) {
            GeoModel.BakedBone bone = model.bakedBones.get(boneIdx);
            for (GeoModel.BakedCube cube : bone.cubes) {
                for (GeoModel.BakedQuad quad : cube.quads) {
                    int vertexOffset = vertices.position() / GpuMeshData.VERTEX_STRIDE;
                    int normal = packNormal(quad.normalX(), quad.normalY(), quad.normalZ());
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
        ByteBuffer indices = MemoryUtil.memAlloc(Math.toIntExact(indexBytes)).order(ByteOrder.nativeOrder());
        ResourceLifecycleStats.onDirectBufferAllocated(null, indexBytes);
        GpuMeshData data = new GpuMeshData(vertices, indices, vertexCount, indexCount, model.bakedBones.size());
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

    private static void closeRange(GpuMeshData data, int partMask, int start, int end) {
        int count = end - start;
        if (count <= 0) {
            return;
        }
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
}
