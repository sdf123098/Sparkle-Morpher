package com.micaftic.morpher.core.gpu;

import com.micaftic.morpher.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.opengl.GlStateManager;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;

public final class IrisRenderPath {
    private static final float[] modelViewScratch = new float[16];


    public static boolean tryRender(GeoModel model, PoseStack.Pose pose, float[] boneParams, int renderPartMask, int packedLight, int packedOverlay, float r, float g, float b, float a, Identifier textureLocation) {
        if (!GpuCapability.isAvailable()) return false;
        if (!BoneXformCompute.ensureCompiled()) return false;
        if (model.bakedBones == null || model.bakedBones.isEmpty()) return false;

        GpuMesh mesh = GpuRenderPath.getOrBuildMesh(model);
        if (mesh == null) return false;
        mesh.ensureXformBuffers();


        ByteBuffer boneBuf = mesh.perFrameBoneBuffer;
        boneBuf.clear();
        GeoModel.nComputeBoneMatricesLocal(mesh.pointer, boneParams, packedLight, boneBuf);
        boneBuf.position(0);
        boneBuf.limit(mesh.boneCount * 144);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, mesh.boneSsbo);
        GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, boneBuf);

        GlStateManager._glUseProgram(BoneXformCompute.program());
        if (BoneXformCompute.locColor() >= 0) GL20.glUniform4f(BoneXformCompute.locColor(), r, g, b, a);
        if (BoneXformCompute.locOverlay() >= 0) GL20.glUniform1i(BoneXformCompute.locOverlay(), packedOverlay);

        if (BoneXformCompute.locModelView() >= 0) {
            pose.pose().get(modelViewScratch);
            GL20.glUniformMatrix4fv(BoneXformCompute.locModelView(), false, modelViewScratch);
        }

        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, mesh.vbo);
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, mesh.xformVbo());
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, mesh.boneSsbo);

        GL43.glDispatchCompute(BoneXformCompute.dispatchGroupCount(mesh.vertexCount), 1, 1);

        GL43.glMemoryBarrier(GL43.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT | GL43.GL_ELEMENT_ARRAY_BARRIER_BIT);

        GlStateManager._glUseProgram(0);

        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, 0);
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, 0);
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, 0);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        RenderType rt = null; // entityCutoutNoCull removed

        ShaderInstance shader = null;
        if (shader == null) {
            return false;
        }

        // ShaderInstance.MODEL_VIEW_MATRIX, PROJECTION_MATRIX, COLOR_MODULATOR, GLINT_ALPHA fields do not exist on stub
        // if (shader.MODEL_VIEW_MATRIX != null) shader.MODEL_VIEW_MATRIX.set(pose.pose());
        // if (shader.PROJECTION_MATRIX != null) shader.PROJECTION_MATRIX.set(new Matrix4f());
        // if (shader.COLOR_MODULATOR != null) shader.COLOR_MODULATOR.set(1.0f, 1.0f, 1.0f, 1.0f);
        // if (shader.GLINT_ALPHA != null) shader.GLINT_ALPHA.set(1.0f);
        // shader.apply();
        // GlStateManager._glBindVertexArray(mesh.xformVao());
        // int offsetBytes = mesh.indexOffsetBytes(renderPartMask);
        // int drawCount = mesh.indexDrawCount(renderPartMask);
        // if (drawCount > 0) {
        //     GL11.glDrawElements(GL11.GL_TRIANGLES, drawCount, GL11.GL_UNSIGNED_INT, offsetBytes);
        // }
        // shader.clear();
        com.mojang.blaze3d.vertex.BufferUploader.invalidate();
        GlStateManager._glBindVertexArray(0);

        return true;
    }
}
