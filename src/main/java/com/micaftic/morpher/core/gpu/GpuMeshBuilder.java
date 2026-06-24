package com.micaftic.morpher.core.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.*;

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
        int vertexCount = meta[0];
        int indexCount = meta[1];
        int boneCount = meta[2];

        ByteBuffer vbuf = GeoModel.nGetGpuMeshVertexBuffer(handle);
        ByteBuffer ibuf = GeoModel.nGetGpuMeshIndexBuffer(handle);
        if (vbuf == null || ibuf == null) {
            GeoModel.nFreeGpuMesh(handle);
            return null;
        }
        vbuf.order(ByteOrder.nativeOrder());
        ibuf.order(ByteOrder.nativeOrder());

        int vao = GL30.glGenVertexArrays();
        int vbo = GlStateManager._glGenBuffers();
        int ibo = GlStateManager._glGenBuffers();
        int ssbo = GlStateManager._glGenBuffers();

        GL30.glBindVertexArray(vao);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vbuf, GL15.GL_STATIC_DRAW);
        GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, ibuf, GL15.GL_STATIC_DRAW);

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
        GL45.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long) boneCount * 144, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        GeoModel.nReleaseGpuMeshScratch(handle);

        return new GpuMesh(handle, vao, vbo, ibo, ssbo, vertexCount, indexCount, boneCount, meta[3], meta[4], meta[5], meta[6], meta[7], meta[8]);
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
