package com.micaftic.morpher.core.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.*;

public final class GpuMeshBuilder {
    public static GpuMesh build(GeoModel model) {
        if (model.bakedBones == null || model.bakedBones.isEmpty()) return null;
        RenderSystem.assertOnRenderThread();
        GpuMeshData meshData = GpuMeshDataBuilder.build(model);
        if (meshData == null) {
            return null;
        }

        try {
            if (meshData.vertexCount <= 0 || meshData.indexCount <= 0 || meshData.boneCount <= 0) {
                GpuDebugLog.warn("mesh build failed: invalid Java mesh vertices={} indices={} bones={}",
                        meshData.vertexCount, meshData.indexCount, meshData.boneCount);
                return null;
            }

            int vao = GL30.glGenVertexArrays();
            int vbo = GlStateManager._glGenBuffers();
            int ibo = GlStateManager._glGenBuffers();
            int[] boneSsbos = new int[GpuMesh.BONE_SSBO_RING_SIZE];

            GL30.glBindVertexArray(vao);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, meshData.vertices, GL15.GL_STATIC_DRAW);
            GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, meshData.indices, GL15.GL_STATIC_DRAW);

            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, GL15.GL_FLOAT, false, GpuMeshData.VERTEX_STRIDE, 0L);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 2, GL15.GL_FLOAT, false, GpuMeshData.VERTEX_STRIDE, 12L);
            GL20.glEnableVertexAttribArray(2);
            GL20.glVertexAttribPointer(2, 4, GL33.GL_INT_2_10_10_10_REV, true, GpuMeshData.VERTEX_STRIDE, 20L);
            GL20.glEnableVertexAttribArray(3);
            GL30.glVertexAttribIPointer(3, 1, GL15.GL_UNSIGNED_SHORT, GpuMeshData.VERTEX_STRIDE, 24L);

            GL20.glEnableVertexAttribArray(4);
            GL20.glVertexAttribPointer(4, 1, GL11.GL_UNSIGNED_BYTE, false, GpuMeshData.VERTEX_STRIDE, 27L);

            GL30.glBindVertexArray(0);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

            for (int i = 0; i < boneSsbos.length; i++) {
                boneSsbos[i] = GlStateManager._glGenBuffers();
                GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, boneSsbos[i]);
                GL45.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long) meshData.boneCount * 144, GL15.GL_STREAM_DRAW);
            }
            GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);

            GpuDebugLog.info("mesh built with Java builder vertices={} indices={} bones={} pm1={}+{} pm2={}+{} pm3={}+{}",
                    meshData.vertexCount, meshData.indexCount, meshData.boneCount,
                    meshData.partMask1Start, meshData.partMask1Count,
                    meshData.partMask2Start, meshData.partMask2Count,
                    meshData.partMask3Start, meshData.partMask3Count);
            GpuDebugLog.glError("mesh build");

            return new GpuMesh(0L, vao, vbo, ibo, boneSsbos, meshData.vertexCount, meshData.indexCount, meshData.boneCount,
                    meshData.partMask1Start, meshData.partMask1Count,
                    meshData.partMask2Start, meshData.partMask2Count,
                    meshData.partMask3Start, meshData.partMask3Count);
        } finally {
            meshData.release();
        }
    }
}
