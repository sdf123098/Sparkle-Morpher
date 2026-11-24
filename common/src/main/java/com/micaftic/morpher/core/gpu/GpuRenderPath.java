package com.micaftic.morpher.core.gpu;

import com.micaftic.morpher.client.render.BuiltModel;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.mixin.client.RenderSystemAccessor;
import com.micaftic.morpher.client.render.RenderContext;
import com.micaftic.morpher.client.renderer.ExtraPlayerRenderProfiler;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;

public final class GpuRenderPath {
    private static final float[] projScratch = new float[16];
    private static final Matrix4f projMVScratch = new Matrix4f();
    private static final Vector3f[] currentLights = new Vector3f[2];
    private static final GpuMeshRegistry<GpuMesh> MESH_REGISTRY = new GpuMeshRegistry<>();
    private static final RenderStateCache stateCache = new RenderStateCache();

    public static boolean tryRender(
            BuiltModel model,
            PoseStack.Pose pose,
            float[] boneParams,
            float[] stateBuffer,
            int renderPartMask,
            int packedLight,
            int packedOverlay,
            float r, float g, float b, float a,
            ResourceLocation textureLocation,
            boolean translucentTexture
    ) {
        if (!GpuCapability.isAvailable()) return false;
        if (!BoneSkinShader.ensureCompiled()) return false;
        if (model.bakedBones == null || model.bakedBones.isEmpty()) return false;
        stateCache.invalidate();

        if (model.gpuMeshHandle == 0) {
            GpuMesh mesh = GpuMeshBuilder.build(model);
            if (mesh == null) return false;
            model.gpuMeshHandle = encodeMeshRef(model, mesh);
        }
        GpuMesh mesh = decodeMeshRef(model.gpuMeshHandle);
        if (mesh == null) {
            model.gpuMeshHandle = 0;
            mesh = GpuMeshBuilder.build(model);
            if (mesh == null) return false;
            model.gpuMeshHandle = encodeMeshRef(model, mesh);
        }

        Matrix4f rootPose = pose.pose();
        Matrix3f rootNormal = pose.normal();
        Matrix4f projMat = RenderSystem.getProjectionMatrix();
        Matrix4f mvMat = RenderSystem.getModelViewMatrix();

        projMat.mul(mvMat, projMVScratch);
        projMVScratch.get(projScratch);

        ByteBuffer boneBuf = mesh.perFrameBoneBuffer;
        boneBuf.clear();

        boolean profileExtraPlayer = ExtraPlayerRenderProfiler.enabled() && RenderContext.isGuiPreview();
        long boneStart = profileExtraPlayer ? System.nanoTime() : 0L;
        if (!BoneMatrixComputer.compute(model, rootPose, rootNormal, boneParams, stateBuffer, packedLight, boneBuf)) {
            return false;
        }
        if (profileExtraPlayer) {
            ExtraPlayerRenderProfiler.recordBoneMatrices(System.nanoTime() - boneStart);
        }
        boneBuf.position(0);
        boneBuf.limit(mesh.boneCount * 144);

        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();

        Minecraft mc = Minecraft.getInstance();
        AbstractTexture modelTex = mc.getTextureManager().getTexture(textureLocation);
        int modelTexId = modelTex.getId();

        stateCache.activeTexture(GL13.GL_TEXTURE0 + 2);
        mc.gameRenderer.lightTexture().turnOnLightLayer();

        stateCache.activeTexture(GL13.GL_TEXTURE0 + 1);
        mc.gameRenderer.overlayTexture().setupOverlayColor();
        GlStateManager._bindTexture(RenderSystem.getShaderTexture(1)); // overlayTexture里的texture没getter，固定bind 1

        stateCache.activeTexture(GL13.GL_TEXTURE0);
        stateCache.bindTexture(modelTexId);

        int boneSsbo = mesh.nextBoneSsbo();
        long uploadStart = profileExtraPlayer ? System.nanoTime() : 0L;
        stateCache.bindSsbo(boneSsbo);
        // Re-specifying STREAM_DRAW storage orphans any backing store still consumed by an
        // earlier world/hand/paper-doll draw. Combined with the per-mesh ring this avoids the
        // implicit CPU/GPU wait caused by overwriting one shared SSBO via glBufferSubData.
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, boneBuf, GL15.GL_STREAM_DRAW);
        stateCache.bindSsboBase(BoneSkinShader.ssbo, boneSsbo);
        if (profileExtraPlayer) {
            ExtraPlayerRenderProfiler.recordSsboUpload(System.nanoTime() - uploadStart);
        }

        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        float[] fogColor = RenderSystem.getShaderFogColor();
        int fogShape = RenderSystem.getShaderFogShape().getIndex();

        stateCache.useProgram(BoneSkinShader.program());
        if (BoneSkinShader.locProj() >= 0) GL20.glUniformMatrix4fv(BoneSkinShader.locProj(), false, projScratch);
        if (BoneSkinShader.locColor() >= 0) GL20.glUniform4f(BoneSkinShader.locColor(), r, g, b, a);
        if (BoneSkinShader.locOverlay() >= 0) GL20.glUniform1i(BoneSkinShader.locOverlay(), packedOverlay);
        if (BoneSkinShader.locFogStart() >= 0) GL20.glUniform1f(BoneSkinShader.locFogStart(), fogStart);
        if (BoneSkinShader.locFogEnd() >= 0) GL20.glUniform1f(BoneSkinShader.locFogEnd(), fogEnd);

        if (BoneSkinShader.locFogColor() >= 0)
            GL20.glUniform4f(BoneSkinShader.locFogColor(), fogColor[0], fogColor[1], fogColor[2], fogColor[3]);

        if (BoneSkinShader.locFogShape() >= 0) GL20.glUniform1i(BoneSkinShader.locFogShape(), fogShape);

        refreshLights();

        if (BoneSkinShader.locLight0() >= 0)
            GL20.glUniform3f(BoneSkinShader.locLight0(), currentLights[0].x, currentLights[0].y, currentLights[0].z);
        if (BoneSkinShader.locLight1() >= 0)
            GL20.glUniform3f(BoneSkinShader.locLight1(), currentLights[1].x, currentLights[1].y, currentLights[1].z);

        long drawStart = profileExtraPlayer ? System.nanoTime() : 0L;
        GlStateManager._glBindVertexArray(mesh.vao);

        int drawCount = mesh.indexDrawCount(renderPartMask);
        if (drawCount > 0 || ((renderPartMask == 1 || renderPartMask == 2) && mesh.partMask3Count > 0)) {
            if (BoneSkinShader.locAlphaMode() >= 0) GL20.glUniform1i(BoneSkinShader.locAlphaMode(), 1);
            drawMeshParts(mesh, renderPartMask);

            if (translucentTexture) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                if (BoneSkinShader.locAlphaMode() >= 0) GL20.glUniform1i(BoneSkinShader.locAlphaMode(), 2);
                drawMeshParts(mesh, renderPartMask);
                RenderSystem.disableBlend();
            }
        }
        if (profileExtraPlayer) {
            ExtraPlayerRenderProfiler.recordDrawSubmission(System.nanoTime() - drawStart);
        }

        stateCache.bindSsboBase(BoneSkinShader.ssbo, 0);
        stateCache.bindSsbo(0);
        stateCache.useProgram(0);

        com.mojang.blaze3d.vertex.BufferUploader.invalidate();
        GlStateManager._glBindVertexArray(0);

        mc.gameRenderer.lightTexture().turnOffLightLayer();
        restoreRenderState();

        return true;
    }

    private static void restoreRenderState() {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
    }

    private static void drawMeshParts(GpuMesh mesh, int renderPartMask) {
        drawMeshPart(mesh.indexOffsetBytes(renderPartMask), mesh.indexDrawCount(renderPartMask));
        if ((renderPartMask == 1 || renderPartMask == 2) && mesh.partMask3Count > 0) {
            drawMeshPart(mesh.partMask3Start * Integer.BYTES, mesh.partMask3Count);
        }
    }

    private static void drawMeshPart(int offsetBytes, int drawCount) {
        if (drawCount > 0) {
            GL11.glDrawElements(GL11.GL_TRIANGLES, drawCount, GL11.GL_UNSIGNED_INT, offsetBytes);
        }
    }

    private static void refreshLights() {
        Vector3f[] arr = RenderSystemAccessor.ysm$getShaderLightDirections();
        currentLights[0] = (arr != null && arr.length > 0 && arr[0] != null) ? arr[0] : new Vector3f(0.2f, 1.0f, -0.7f).normalize();
        currentLights[1] = (arr != null && arr.length > 1 && arr[1] != null) ? arr[1] : new Vector3f(-0.2f, 1.0f, 0.7f).normalize();
    }


    public static void disposeMesh(BuiltModel model) {
        if (model.gpuMeshHandle == 0) return;
        GpuMesh mesh = MESH_REGISTRY.remove(model.gpuMeshHandle);
        if (mesh != null) mesh.dispose();
        model.gpuMeshHandle = 0;
    }

    /**
     * R10.2：释放指定模型（BuiltModel，即租约 owner）名下的全部 GPU mesh。
     * 与 {@link #disposeMesh} 等价（每个 BuiltModel 至多一个 mesh），但语义按 owner 释放，
     * 供 RenderBackend.release 走统一的租约 revoke 路径。
     */
    public static void disposeOwner(BuiltModel model) {
        if (model.gpuMeshHandle != 0) {
            disposeMesh(model);
            return;
        }
        for (GpuMesh mesh : MESH_REGISTRY.releaseOwner(model)) {
            mesh.dispose();
        }
    }

    /**
     * R10.2：回收孤儿 GPU mesh（owner 弱引用已失效 / 无 owner 的残留），GC 兜底防泄漏。
     * 正常路径由模型装配释放显式 disposeMesh，本方法只兜底异常/替换路径。
     */
    public static void sweepOrphanedMeshes(String reason) {
        if (!RenderSystem.isOnRenderThread()) {
            Minecraft.getInstance().execute(() -> sweepOrphanedMeshes(reason));
            return;
        }
        int released = 0;
        long estimatedBytes = 0L;
        for (GpuMesh mesh : MESH_REGISTRY.sweepOrphans()) {
            if (mesh != null) {
                estimatedBytes += mesh.estimatedBytes;
                mesh.dispose();
                released++;
            }
        }
        if (released > 0) {
            YesSteveModel.LOGGER.info("[SM] Released {} orphan GPU meshes (estimated {} bytes) due to {}", released, estimatedBytes, reason);
        }
    }

    public static void disposeAllMeshes(String reason) {
        if (!RenderSystem.isOnRenderThread()) {
            Minecraft.getInstance().execute(() -> disposeAllMeshes(reason));
            return;
        }
        int released = 0;
        long estimatedBytes = 0L;
        for (GpuMesh mesh : MESH_REGISTRY.clearAll()) {
            if (mesh != null) {
                estimatedBytes += mesh.estimatedBytes;
                mesh.dispose();
                released++;
            }
        }
        if (released > 0) {
            YesSteveModel.LOGGER.info("[SM] Released {} GPU meshes (estimated {} bytes) due to {}", released, estimatedBytes, reason);
        }
    }

    public static GpuMesh getOrBuildMesh(BuiltModel model) {
        if (model.gpuMeshHandle == 0) {
            GpuMesh mesh = GpuMeshBuilder.build(model);
            if (mesh == null) return null;
            model.gpuMeshHandle = encodeMeshRef(model, mesh);
        }
        GpuMesh mesh = decodeMeshRef(model.gpuMeshHandle);
        if (mesh == null) {
            model.gpuMeshHandle = 0;
            return getOrBuildMesh(model);
        }
        return mesh;
    }

    private static long encodeMeshRef(BuiltModel owner, GpuMesh mesh) {
        return MESH_REGISTRY.register(owner, mesh);
    }

    private static GpuMesh decodeMeshRef(long ref) {
        return MESH_REGISTRY.get(ref);
    }

    private static final class RenderStateCache {
        private final int[] textureIds = new int[8];
        private final int[] ssboBases = new int[8];
        private int activeTexture = -1;
        private int ssbo = -1;
        private int program = -1;

        void invalidate() {
            java.util.Arrays.fill(textureIds, -1);
            java.util.Arrays.fill(ssboBases, -1);
            activeTexture = -1;
            ssbo = -1;
            program = -1;
        }

        void activeTexture(int id) {
            if (activeTexture == id) {
                return;
            }
            GlStateManager._activeTexture(id);
            activeTexture = id;
        }

        void bindTexture(int id) {
            int unit = Math.max(0, activeTexture - GL13.GL_TEXTURE0);
            if (unit >= textureIds.length) {
                GlStateManager._bindTexture(id);
                return;
            }
            if (textureIds[unit] == id) {
                return;
            }
            GlStateManager._bindTexture(id);
            textureIds[unit] = id;
        }

        void bindSsbo(int id) {
            if (ssbo == id) {
                return;
            }
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
            ssbo = id;
        }

        void bindSsboBase(int index, int id) {
            if (index >= 0 && index < ssboBases.length && ssboBases[index] == id) {
                return;
            }
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, id);
            if (index >= 0 && index < ssboBases.length) {
                ssboBases[index] = id;
            }
        }

        void useProgram(int id) {
            if (program == id) {
                return;
            }
            GlStateManager._glUseProgram(id);
            program = id;
        }
    }
}
