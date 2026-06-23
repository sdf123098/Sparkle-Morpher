package com.micaftic.morpher.core.gpu;

import com.micaftic.morpher.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.*;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class GpuMeshBuilder {
    public static GpuMesh build(GeoModel model) {
        if (model.bakedBones == null || model.bakedBones.isEmpty()) return null;
        RenderSystem.assertOnRenderThread();
        ByteBuffer modelBuf = serializeModel(model);
        int[] meta = new int[9];
        long handle = GeoModel.nBuildGpuMesh(modelBuf, meta);
        if (handle == 0) {
            return null;
        }

        MeshBuffers meshBuffers = buildMeshBuffers(model);
        if (meshBuffers == null) {
            GeoModel.nFreeGpuMesh(handle);
            return null;
        }

        int vao = GL30.glGenVertexArrays();
        int vbo = GlStateManager._glGenBuffers();
        int ibo = GlStateManager._glGenBuffers();
        int ssbo = GlStateManager._glGenBuffers();

        GL30.glBindVertexArray(vao);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, meshBuffers.vertices, GL15.GL_STATIC_DRAW);
        GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, meshBuffers.indices, GL15.GL_STATIC_DRAW);

        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL15.GL_FLOAT, false, 32, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL15.GL_FLOAT, false, 32, 12L);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 4, GL33.GL_INT_2_10_10_10_REV, true, 32, 20L);
        GL20.glEnableVertexAttribArray(3);
        GL30.glVertexAttribIPointer(3, 1, GL15.GL_UNSIGNED_SHORT, 32, 24L);

        GL20.glEnableVertexAttribArray(4);
        GL20.glVertexAttribPointer(4, 1, GL11.GL_UNSIGNED_BYTE, false, 32, 27L);

        GL30.glBindVertexArray(0);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssbo);
        GL45.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long) meshBuffers.boneCount * 144, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        GeoModel.nReleaseGpuMeshScratch(handle);

        return new GpuMesh(handle, vao, vbo, ibo, ssbo, meshBuffers.vertexCount, meshBuffers.indexCount, meshBuffers.boneCount, meshBuffers.partMask1Start, meshBuffers.partMask1Count, meshBuffers.partMask2Start, meshBuffers.partMask2Count, meshBuffers.partMask3Start, meshBuffers.partMask3Count);
    }

    private static MeshBuffers buildMeshBuffers(GeoModel model) {
        int totalQuads = 0;
        for (GeoModel.BakedBone bone : model.bakedBones) {
            for (GeoModel.BakedCube cube : bone.cubes) {
                totalQuads += cube.quads.size();
            }
        }
        if (totalQuads <= 0) {
            return null;
        }

        int vertexCount = totalQuads * 4;
        int indexCount = totalQuads * 6;
        ByteBuffer vertices = ByteBuffer.allocateDirect(vertexCount * 32).order(ByteOrder.nativeOrder());
        int[] quadBases = new int[totalQuads];
        int[] quadMasks = new int[totalQuads];
        int[] partIndexCounts = new int[4];

        int quadIndex = 0;
        int vertexIndex = 0;
        for (int boneIdx = 0; boneIdx < model.bakedBones.size(); boneIdx++) {
            GeoModel.BakedBone bone = model.bakedBones.get(boneIdx);
            int partMask = normalizePartMask(bone.partMask);
            for (GeoModel.BakedCube cube : bone.cubes) {
                for (GeoModel.BakedQuad quad : cube.quads) {
                    quadBases[quadIndex] = vertexIndex;
                    quadMasks[quadIndex] = partMask;
                    partIndexCounts[partMask] += 6;
                    for (int v = 0; v < 4; v++) {
                        putVertex(vertices, quad.positions[v], quad.uvs[v], quad.normal, boneIdx, cube.cullable);
                    }
                    quadIndex++;
                    vertexIndex += 4;
                }
            }
        }
        vertices.flip();

        ByteBuffer indices = ByteBuffer.allocateDirect(indexCount * Integer.BYTES).order(ByteOrder.nativeOrder());
        int partMask1Start = partIndexCounts[0];
        int partMask2Start = partMask1Start + partIndexCounts[1];
        int partMask3Start = partMask2Start + partIndexCounts[2];

        putIndicesForPart(indices, quadBases, quadMasks, 0);
        putIndicesForPart(indices, quadBases, quadMasks, 1);
        putIndicesForPart(indices, quadBases, quadMasks, 2);
        putIndicesForPart(indices, quadBases, quadMasks, 3);
        indices.flip();

        return new MeshBuffers(
                vertices,
                indices,
                vertexCount,
                indexCount,
                model.bakedBones.size(),
                partMask1Start,
                partIndexCounts[1],
                partMask2Start,
                partIndexCounts[2],
                partMask3Start,
                partIndexCounts[3]
        );
    }

    private static int normalizePartMask(int partMask) {
        return partMask >= 1 && partMask <= 3 ? partMask : 0;
    }

    private static void putVertex(ByteBuffer vertices, Vector3f position, Vector2f uv, Vector3f normal, int boneIdx, boolean cullable) {
        vertices.putFloat(position.x());
        vertices.putFloat(position.y());
        vertices.putFloat(position.z());
        vertices.putFloat(uv.x());
        vertices.putFloat(uv.y());
        vertices.putInt(packNormal(normal));
        vertices.putShort((short) boneIdx);
        vertices.put((byte) 0);
        vertices.put((byte) (cullable ? 1 : 0));
        vertices.putInt(0);
    }

    private static int packNormal(Vector3f normal) {
        int x = packSigned10(normal.x());
        int y = packSigned10(normal.y());
        int z = packSigned10(normal.z());
        return (x & 0x3FF) | ((y & 0x3FF) << 10) | ((z & 0x3FF) << 20);
    }

    private static int packSigned10(float value) {
        if (!Float.isFinite(value)) {
            return 0;
        }
        int packed = Math.round(Math.max(-1.0f, Math.min(1.0f, value)) * 511.0f);
        return Math.max(-512, Math.min(511, packed));
    }

    private static void putIndicesForPart(ByteBuffer indices, int[] quadBases, int[] quadMasks, int partMask) {
        for (int i = 0; i < quadBases.length; i++) {
            if (quadMasks[i] == partMask) {
                putQuadIndices(indices, quadBases[i]);
            }
        }
    }

    private static void putQuadIndices(ByteBuffer indices, int base) {
        indices.putInt(base);
        indices.putInt(base + 1);
        indices.putInt(base + 2);
        indices.putInt(base + 2);
        indices.putInt(base + 3);
        indices.putInt(base);
    }

    private static final class MeshBuffers {
        final ByteBuffer vertices;
        final ByteBuffer indices;
        final int vertexCount;
        final int indexCount;
        final int boneCount;
        final int partMask1Start;
        final int partMask1Count;
        final int partMask2Start;
        final int partMask2Count;
        final int partMask3Start;
        final int partMask3Count;

        MeshBuffers(ByteBuffer vertices, ByteBuffer indices, int vertexCount, int indexCount, int boneCount, int partMask1Start, int partMask1Count, int partMask2Start, int partMask2Count, int partMask3Start, int partMask3Count) {
            this.vertices = vertices;
            this.indices = indices;
            this.vertexCount = vertexCount;
            this.indexCount = indexCount;
            this.boneCount = boneCount;
            this.partMask1Start = partMask1Start;
            this.partMask1Count = partMask1Count;
            this.partMask2Start = partMask2Start;
            this.partMask2Count = partMask2Count;
            this.partMask3Start = partMask3Start;
            this.partMask3Count = partMask3Count;
        }
    }

    private static ByteBuffer serializeModel(GeoModel model) {
        int totalBones = model.bakedBones.size();
        int totalCubes = 0;
        int totalQuads = 0;
        for (GeoModel.BakedBone bone : model.bakedBones) {
            totalCubes += bone.cubes.size();
            for (GeoModel.BakedCube cube : bone.cubes) {
                totalQuads += cube.quads.size();
            }
        }
        int sz = 4 + (totalBones * 25) + (totalCubes * 5) + (totalQuads * 92);
        ByteBuffer buf = ByteBuffer.allocateDirect(sz).order(ByteOrder.nativeOrder());
        buf.putInt(totalBones);
        for (GeoModel.BakedBone bone : model.bakedBones) {
            buf.putInt(bone.parentIdx);
            buf.putInt(bone.partMask);
            buf.put((byte) (bone.glow ? 1 : 0));
            buf.putFloat(bone.pivotX);
            buf.putFloat(bone.pivotY);
            buf.putFloat(bone.pivotZ);
            buf.putInt(bone.cubes.size());
            for (GeoModel.BakedCube cube : bone.cubes) {
                buf.put((byte) (cube.cullable ? 1 : 0));
                buf.putInt(cube.quads.size());
                for (GeoModel.BakedQuad quad : cube.quads) {
                    for (int v = 0; v < 4; v++) {
                        buf.putFloat(quad.positions[v].x());
                        buf.putFloat(quad.positions[v].y());
                        buf.putFloat(quad.positions[v].z());
                    }
                    for (int v = 0; v < 4; v++) {
                        buf.putFloat(quad.uvs[v].x());
                        buf.putFloat(quad.uvs[v].y());
                    }
                    buf.putFloat(quad.normal.x());
                    buf.putFloat(quad.normal.y());
                    buf.putFloat(quad.normal.z());
                }
            }
        }
        buf.position(0);
        return buf;
    }
}
