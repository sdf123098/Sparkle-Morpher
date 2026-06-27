package com.micaftic.morpher.core.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.mixin.client.RenderSystemAccessor;
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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class GpuRenderPath {
    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;
    private static final float[] rootPoseScratch = new float[16];
    private static final float[] rootNormalScratch = new float[9];
    private static final float[] projScratch = new float[16];
    private static final float[] boneMatrix4Scratch = new float[16];
    private static final float[] boneMatrix3Scratch = new float[9];
    private static final Matrix4f projMVScratch = new Matrix4f();
    private static final Matrix4f identityScratch = new Matrix4f();
    private static final Matrix4f globalBoneScratch = new Matrix4f();
    private static final Matrix3f localNormalScratchMat = new Matrix3f();
    private static final Matrix3f globalNormalScratchMat = new Matrix3f();
    private static final Vector3f[] currentLights = new Vector3f[2];
    private static final ConcurrentHashMap<Long, GpuMesh> meshMap = new ConcurrentHashMap<>();
    private static final AtomicLong ref = new AtomicLong(1);
    private static final Matrix4f pivotAbsScratchMat = new Matrix4f();
    private static int[] pivotAbsPathScratch = new int[64];
    private static Matrix4f[] boneLocalScratch = new Matrix4f[0];
    private static boolean[] boneComputedScratch = new boolean[0];
    private static boolean[] boneVisibleScratch = new boolean[0];
    private static final RenderStateCache stateCache = new RenderStateCache();

    public static boolean tryRender(
            GeoModel model,
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
            model.gpuMeshHandle = encodeMeshRef(mesh);
        }
        GpuMesh mesh = decodeMeshRef(model.gpuMeshHandle);
        if (mesh == null) {
            model.gpuMeshHandle = 0;
            mesh = GpuMeshBuilder.build(model);
            if (mesh == null) return false;
            model.gpuMeshHandle = encodeMeshRef(mesh);
        }

        Matrix4f rootPose = pose.pose();
        Matrix3f rootNormal = pose.normal();
        Matrix4f projMat = RenderSystem.getProjectionMatrix();
        Matrix4f mvMat = RenderSystem.getModelViewMatrix();

        rootPose.get(rootPoseScratch);
        rootNormal.get(rootNormalScratch);
        projMat.mul(mvMat, projMVScratch);
        projMVScratch.get(projScratch);

        ByteBuffer boneBuf = mesh.perFrameBoneBuffer;
        boneBuf.clear();

        if (!computeBoneMatrices(model, rootPose, rootNormal, boneParams, stateBuffer, packedLight, boneBuf)) {
            return false;
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

        stateCache.bindSsbo(mesh.boneSsbo);
        GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, boneBuf);
        stateCache.bindSsboBase(BoneSkinShader.ssbo, mesh.boneSsbo);

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

    private static boolean computeBoneMatrices(
            GeoModel model,
            Matrix4f rootPose,
            Matrix3f rootNormal,
            float[] boneParams,
            float[] stateBuffer,
            int packedLight,
            ByteBuffer out
    ) {
        int boneCount = model.bakedBones.size();
        if (boneParams == null || boneParams.length < boneCount * 12) {
            return false;
        }

        ensureBoneScratch(boneCount);
        Arrays.fill(boneVisibleScratch, 0, boneCount, false);

        int[] boneOrder = model.bakedBoneOrder;
        if (boneOrder != null && boneOrder.length == boneCount) {
            for (int orderIndex = 0; orderIndex < boneCount; orderIndex++) {
                computeBoneLocalTransformLinear(boneOrder[orderIndex], model.bakedBones, boneParams, stateBuffer);
            }
        } else {
            Arrays.fill(boneComputedScratch, 0, boneCount, false);
            for (int i = 0; i < boneCount; i++) {
                computeBoneLocalTransform(i, model.bakedBones, boneParams, stateBuffer);
            }
        }

        for (int i = 0; i < boneCount; i++) {
            GeoModel.BakedBone bone = model.bakedBones.get(i);
            boolean isHidden = !boneVisibleScratch[i];

            if (isHidden) {
                writeMatrix4(out, identityScratch.identity());
                writeIdentityNormal(out);
                out.putInt(0);
                out.putInt(1);
                out.putInt(0);
                out.putInt(0);
                continue;
            }

            Matrix4f localBoneMat = boneLocalScratch[i];
            globalBoneScratch.set(rootPose).mul(localBoneMat);
            writeMatrix4(out, globalBoneScratch);

            localBoneMat.normal(localNormalScratchMat);
            globalNormalScratchMat.set(rootNormal).mul(localNormalScratchMat);
            writeMatrix3AsMatrix4(out, globalNormalScratchMat);

            out.putInt(bone.glow ? FULL_BRIGHT_LIGHT : packedLight);
            out.putInt(0);
            out.putInt(0);
            out.putInt(0);
        }

        return true;
    }

    private static void ensureBoneScratch(int boneCount) {
        if (boneLocalScratch.length < boneCount) {
            boneLocalScratch = Arrays.copyOf(boneLocalScratch, boneCount);
            boneComputedScratch = Arrays.copyOf(boneComputedScratch, boneCount);
            boneVisibleScratch = Arrays.copyOf(boneVisibleScratch, boneCount);
        }
    }

    private static void computeBoneLocalTransformLinear(int idx, List<GeoModel.BakedBone> bones, float[] boneParams, float[] stateBuffer) {
        GeoModel.BakedBone bone = bones.get(idx);
        Matrix4f parentMatrix = identityScratch.identity();
        boolean isVisible = true;

        if (bone.parentIdx != -1) {
            parentMatrix = boneLocalScratch[bone.parentIdx];
            if (!boneVisibleScratch[bone.parentIdx]) {
                isVisible = false;
            }
        }

        Matrix4f localMat = boneLocalScratch[idx];
        if (localMat == null) {
            localMat = new Matrix4f();
            boneLocalScratch[idx] = localMat;
        }
        localMat.set(parentMatrix);

        int pOffset = idx * 12;
        float animRx = boneParams[pOffset];
        float animRy = boneParams[pOffset + 1];
        float animRz = boneParams[pOffset + 2];
        float animTx = boneParams[pOffset + 3];
        float animTy = boneParams[pOffset + 4];
        float animTz = boneParams[pOffset + 5];
        float animSx = boneParams[pOffset + 6];
        float animSy = boneParams[pOffset + 7];
        float animSz = boneParams[pOffset + 8];
        float unk3 = boneParams[pOffset + 11];

        if (animSx == 0.0f && animSy == 0.0f && animSz == 0.0f) {
            isVisible = false;
        }

        localMat.translate(
                (bone.pivotX - animTx) * 0.0625f,
                (bone.pivotY + animTy) * 0.0625f,
                (bone.pivotZ + animTz) * 0.0625f
        );
        localMat.rotateZ(animRz);
        localMat.rotateY(animRy);
        localMat.rotateX(animRx);

        if (animSx != 1.0f || animSy != 1.0f || animSz != 1.0f) {
            localMat.scale(animSx, animSy, animSz);
        }

        if (unk3 == 1.0F && stateBuffer != null && isVisible) {
            int stateOffset = idx * 4;
            if (stateOffset + 2 < stateBuffer.length) {
                stateBuffer[stateOffset] = -localMat.m30() * 16.0f;
                stateBuffer[stateOffset + 1] = localMat.m31() * 16.0f;
                stateBuffer[stateOffset + 2] = localMat.m32() * 16.0f;
            }
        }

        localMat.translate(-bone.pivotX / 16.0f, -bone.pivotY / 16.0f, -bone.pivotZ / 16.0f);
        boneVisibleScratch[idx] = isVisible;
    }

    private static Matrix4f computeBoneLocalTransform(int idx, List<GeoModel.BakedBone> bones, float[] boneParams, float[] stateBuffer) {
        if (boneComputedScratch[idx]) {
            return boneLocalScratch[idx];
        }

        GeoModel.BakedBone bone = bones.get(idx);
        Matrix4f parentMatrix = identityScratch.identity();
        boolean isVisible = true;

        if (bone.parentIdx != -1) {
            parentMatrix = computeBoneLocalTransform(bone.parentIdx, bones, boneParams, stateBuffer);
            if (!boneVisibleScratch[bone.parentIdx]) {
                isVisible = false;
            }
        }

        Matrix4f localMat = boneLocalScratch[idx];
        if (localMat == null) {
            localMat = new Matrix4f();
            boneLocalScratch[idx] = localMat;
        }
        localMat.set(parentMatrix);

        int pOffset = idx * 12;
        float animRx = boneParams[pOffset];
        float animRy = boneParams[pOffset + 1];
        float animRz = boneParams[pOffset + 2];
        float animTx = boneParams[pOffset + 3];
        float animTy = boneParams[pOffset + 4];
        float animTz = boneParams[pOffset + 5];
        float animSx = boneParams[pOffset + 6];
        float animSy = boneParams[pOffset + 7];
        float animSz = boneParams[pOffset + 8];
        float unk3 = boneParams[pOffset + 11];

        if (animSx == 0.0f && animSy == 0.0f && animSz == 0.0f) {
            isVisible = false;
        }

        localMat.translate(
                (bone.pivotX - animTx) * 0.0625f,
                (bone.pivotY + animTy) * 0.0625f,
                (bone.pivotZ + animTz) * 0.0625f
        );
        localMat.rotateZ(animRz);
        localMat.rotateY(animRy);
        localMat.rotateX(animRx);

        if (animSx != 1.0f || animSy != 1.0f || animSz != 1.0f) {
            localMat.scale(animSx, animSy, animSz);
        }

        if (unk3 == 1.0F && stateBuffer != null && isVisible) {
            int stateOffset = idx * 4;
            if (stateOffset + 2 < stateBuffer.length) {
                stateBuffer[stateOffset] = -localMat.m30() * 16.0f;
                stateBuffer[stateOffset + 1] = localMat.m31() * 16.0f;
                stateBuffer[stateOffset + 2] = localMat.m32() * 16.0f;
            }
        }

        localMat.translate(-bone.pivotX / 16.0f, -bone.pivotY / 16.0f, -bone.pivotZ / 16.0f);

        boneVisibleScratch[idx] = isVisible;
        boneComputedScratch[idx] = true;
        return localMat;
    }

    private static void writeMatrix4(ByteBuffer out, Matrix4f matrix) {
        matrix.get(boneMatrix4Scratch);
        for (int i = 0; i < 16; i++) {
            out.putFloat(boneMatrix4Scratch[i]);
        }
    }

    private static void writeMatrix3AsMatrix4(ByteBuffer out, Matrix3f matrix) {
        matrix.get(boneMatrix3Scratch);
        out.putFloat(boneMatrix3Scratch[0]);
        out.putFloat(boneMatrix3Scratch[1]);
        out.putFloat(boneMatrix3Scratch[2]);
        out.putFloat(0.0f);
        out.putFloat(boneMatrix3Scratch[3]);
        out.putFloat(boneMatrix3Scratch[4]);
        out.putFloat(boneMatrix3Scratch[5]);
        out.putFloat(0.0f);
        out.putFloat(boneMatrix3Scratch[6]);
        out.putFloat(boneMatrix3Scratch[7]);
        out.putFloat(boneMatrix3Scratch[8]);
        out.putFloat(0.0f);
        out.putFloat(0.0f);
        out.putFloat(0.0f);
        out.putFloat(0.0f);
        out.putFloat(1.0f);
    }

    private static void writeIdentityNormal(ByteBuffer out) {
        out.putFloat(1.0f);
        out.putFloat(0.0f);
        out.putFloat(0.0f);
        out.putFloat(0.0f);
        out.putFloat(0.0f);
        out.putFloat(1.0f);
        out.putFloat(0.0f);
        out.putFloat(0.0f);
        out.putFloat(0.0f);
        out.putFloat(0.0f);
        out.putFloat(1.0f);
        out.putFloat(0.0f);
        out.putFloat(0.0f);
        out.putFloat(0.0f);
        out.putFloat(0.0f);
        out.putFloat(1.0f);
    }

    public static void disposeMesh(GeoModel model) {
        if (model.gpuMeshHandle == 0) return;
        GpuMesh mesh = meshMap.remove(model.gpuMeshHandle);
        if (mesh != null) mesh.dispose();
        model.gpuMeshHandle = 0;
    }

    public static void disposeAllMeshes(String reason) {
        if (!RenderSystem.isOnRenderThread()) {
            Minecraft.getInstance().execute(() -> disposeAllMeshes(reason));
            return;
        }
        int released = 0;
        long estimatedBytes = 0L;
        for (Long handle : meshMap.keySet()) {
            GpuMesh mesh = meshMap.remove(handle);
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

    public static GpuMesh getOrBuildMesh(GeoModel model) {
        if (model.gpuMeshHandle == 0) {
            GpuMesh mesh = GpuMeshBuilder.build(model);
            if (mesh == null) return null;
            model.gpuMeshHandle = encodeMeshRef(mesh);
        }
        GpuMesh mesh = decodeMeshRef(model.gpuMeshHandle);
        if (mesh == null) {
            model.gpuMeshHandle = 0;
            return getOrBuildMesh(model);
        }
        return mesh;
    }

    private static long encodeMeshRef(GpuMesh mesh) {
        long ref = GpuRenderPath.ref.getAndIncrement();
        meshMap.put(ref, mesh);
        return ref;
    }

    private static GpuMesh decodeMeshRef(long ref) {
        return meshMap.get(ref);
    }

    private static void updatePivotAbsStateBuffer(GeoModel model, float[] boneParams, float[] stateBuffer) {
        if (stateBuffer == null || boneParams == null) return;
        if (model.bakedBones == null || model.bakedBones.isEmpty()) return;

        int boneCount = model.bakedBones.size();

        for (int i = 0; i < boneCount; i++) {
            int pOffset = i * 12;
            if (pOffset + 11 >= boneParams.length) break;

            float unk3 = boneParams[pOffset + 11];
            if (unk3 != 1.0f) continue;

            int sOffset = i * 4;
            if (sOffset + 2 >= stateBuffer.length) continue;

            computeOnePivotAbs(i, model.bakedBones, boneParams, stateBuffer, sOffset);
        }
    }

    private static void computeOnePivotAbs(int targetIdx, List<GeoModel.BakedBone> bones, float[] boneParams, float[] stateBuffer, int stateOffset) {
        int depth = 0;
        int idx = targetIdx;

        while (idx != -1) {
            if (depth >= pivotAbsPathScratch.length) {
                int[] newPath = new int[pivotAbsPathScratch.length * 2];
                System.arraycopy(pivotAbsPathScratch, 0, newPath, 0, pivotAbsPathScratch.length);
                pivotAbsPathScratch = newPath;
            }

            pivotAbsPathScratch[depth++] = idx;
            idx = bones.get(idx).parentIdx;
        }

        Matrix4f localMat = pivotAbsScratchMat.identity();
        boolean isVisible = true;

        for (int p = depth - 1; p >= 0; p--) {
            int boneIdx = pivotAbsPathScratch[p];
            GeoModel.BakedBone bone = bones.get(boneIdx);

            int pOffset = boneIdx * 12;
            if (pOffset + 11 >= boneParams.length) return;

            float animRx = boneParams[pOffset];
            float animRy = boneParams[pOffset + 1];
            float animRz = boneParams[pOffset + 2];
            float animTx = boneParams[pOffset + 3];
            float animTy = boneParams[pOffset + 4];
            float animTz = boneParams[pOffset + 5];
            float animSx = boneParams[pOffset + 6];
            float animSy = boneParams[pOffset + 7];
            float animSz = boneParams[pOffset + 8];

            if (animSx == 0.0f && animSy == 0.0f && animSz == 0.0f) {
                isVisible = false;
            }

            if (!isVisible) {
                return;
            }

            localMat.translate((bone.pivotX - animTx) * 0.0625f, (bone.pivotY + animTy) * 0.0625f, (bone.pivotZ + animTz) * 0.0625f);

            localMat.rotateZ(animRz);
            localMat.rotateY(animRy);
            localMat.rotateX(animRx);

            if (animSx != 1.0f || animSy != 1.0f || animSz != 1.0f) {
                localMat.scale(animSx, animSy, animSz);
            }

            if (boneIdx == targetIdx) {
                stateBuffer[stateOffset] = -localMat.m30() * 16.0f;
                stateBuffer[stateOffset + 1] = localMat.m31() * 16.0f;
                stateBuffer[stateOffset + 2] = localMat.m32() * 16.0f;
                return;
            }

            localMat.translate(-bone.pivotX / 16.0f, -bone.pivotY / 16.0f, -bone.pivotZ / 16.0f);
        }
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
