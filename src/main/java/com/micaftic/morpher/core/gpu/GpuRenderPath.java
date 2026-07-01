package com.micaftic.morpher.core.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.client.renderer.WorldRenderState;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.core.acceleration.AccelerationCapability;
import com.micaftic.morpher.core.render.SmGraphicsBackendDetector;
import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
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
    private static final float[] fogColorScratch = new float[4];
    private static final Vector3f light0Scratch = new Vector3f(0.2f, 1.0f, -0.7f).normalize();
    private static final Vector3f light1Scratch = new Vector3f(-0.2f, 1.0f, 0.7f).normalize();
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
            Identifier textureLocation,
            boolean translucentTexture
    ) {
        long frameId = GpuDebugLog.nextFrame();
        if (translucentTexture) {
            GpuDebugLog.verbose("frame={} fallback: translucent texture={}", frameId, textureLocation);
            return false;
        }
        if (!SmGraphicsBackendDetector.isRawOpenGlAllowed()) {
            disposeAllMeshes("raw OpenGL disabled: " + SmGraphicsBackendDetector.currentBackend());
            GpuDebugLog.warn("frame={} fallback: raw OpenGL disabled backend={} reason={}", frameId,
                    SmGraphicsBackendDetector.currentBackend(), SmGraphicsBackendDetector.reason());
            return false;
        }
        if (!GpuCapability.isAvailable()) {
            GpuDebugLog.warn("frame={} fallback: GPU unavailable reason={}", frameId, GpuCapability.getReason());
            return false;
        }
        if (!BoneSkinShader.ensureCompiled()) {
            GpuDebugLog.warn("frame={} fallback: BoneSkinShader compile unavailable", frameId);
            return false;
        }
        if (model.bakedBones == null || model.bakedBones.isEmpty()) {
            GpuDebugLog.verbose("frame={} fallback: no baked bones texture={}", frameId, textureLocation);
            return false;
        }
        stateCache.invalidate();

        if (model.gpuMeshHandle == 0) {
            GpuDebugLog.info("frame={} building mesh bones={} texture={}", frameId, model.bakedBones.size(), textureLocation);
            GpuMesh mesh = GpuMeshBuilder.build(model);
            if (mesh == null) {
                GpuDebugLog.warn("frame={} fallback: mesh build returned null texture={}", frameId, textureLocation);
                return false;
            }
            model.gpuMeshHandle = encodeMeshRef(mesh);
        }
        GpuMesh mesh = decodeMeshRef(model.gpuMeshHandle);
        if (mesh == null) {
            GpuDebugLog.warn("frame={} fallback: mesh ref missing ref={} texture={}", frameId, model.gpuMeshHandle, textureLocation);
            model.gpuMeshHandle = 0;
            GpuMesh rebuilt = GpuMeshBuilder.build(model);
            if (rebuilt == null) {
                return false;
            }
            model.gpuMeshHandle = encodeMeshRef(rebuilt);
            mesh = rebuilt;
        }

        int drawCount = mesh.indexDrawCount(renderPartMask);
        if (drawCount <= 0 && (renderPartMask == 0 || renderPartMask == 3 || mesh.partMask3Count <= 0)) {
            GpuDebugLog.warn("frame={} fallback: drawCount={} partMask={} meshIndices={} pm1={} pm2={} pm3={}",
                    frameId, drawCount, renderPartMask, mesh.indexCount, mesh.partMask1Count, mesh.partMask2Count, mesh.partMask3Count);
            return false;
        }

        Matrix4f rootPose = pose.pose();
        Matrix3f rootNormal = pose.normal();
        Matrix4f projMat = projMVScratch;
        if (!WorldRenderState.getProjectionMatrix(projMat)) {
            GpuDebugLog.warn("frame={} fallback: missing world projection matrix texture={}", frameId, textureLocation);
            return false;
        }
        projMat.get(projScratch);

        ByteBuffer boneBuf = mesh.perFrameBoneBuffer;
        boneBuf.clear();

        if (GeneralConfig.safeGet(GeneralConfig.NATIVE_SIMD_POLICY, GeneralConfig.NativeSimdPolicy.AGGRESSIVE) != GeneralConfig.NativeSimdPolicy.OFF && AccelerationCapability.isLoaded()) {
            if (!computeBoneMatricesNative(model, mesh, rootPose, rootNormal, boneParams, stateBuffer, packedLight, boneBuf)) {
                GpuDebugLog.warn("frame={} native bone matrices failed; using Java fallback bones={} meshPointer={} boneParamsLen={}",
                        frameId, model.bakedBones.size(), mesh.pointer, boneParams == null ? -1 : boneParams.length);
                boneBuf.clear();
                if (!computeBoneMatrices(model, rootPose, rootNormal, boneParams, stateBuffer, packedLight, boneBuf)) {
                    GpuDebugLog.warn("frame={} fallback: Java bone matrices failed bones={} boneParamsLen={}",
                            frameId, model.bakedBones.size(), boneParams == null ? -1 : boneParams.length);
                    return false;
                }
            }
        } else if (!computeBoneMatrices(model, rootPose, rootNormal, boneParams, stateBuffer, packedLight, boneBuf)) {
            GpuDebugLog.warn("frame={} fallback: Java bone matrices failed bones={} boneParamsLen={}",
                    frameId, model.bakedBones.size(), boneParams == null ? -1 : boneParams.length);
            return false;
        }
        boneBuf.position(0);
        boneBuf.limit(mesh.boneCount * 144);

        Minecraft mc = Minecraft.getInstance();
        AbstractTexture modelTex = mc.getTextureManager().getTexture(textureLocation);
        TextureBinding modelTexture = resolveTextureBinding(modelTex);
        int modelSamplerId = resolveSamplerId(modelTex != null ? modelTex.getSampler() : null);
        TextureBinding overlayTexture = resolveOverlayTexture(mc.gameRenderer.overlayTexture());
        int clampSamplerId = resolveClampSamplerId();
        TextureBinding lightmapTexture = resolveLightmapTexture(mc);
        if (!modelTexture.isValid() || modelSamplerId == 0 || !overlayTexture.isValid() || clampSamplerId == 0 || !lightmapTexture.isValid()) {
            GpuDebugLog.warn("frame={} fallback: invalid texture binding modelTex={} modelSampler={} overlayTex={} clampSampler={} lightmapTex={} texture={}",
                    frameId, modelTexture.isValid(), modelSamplerId, overlayTexture.isValid(), clampSamplerId, lightmapTexture.isValid(), textureLocation);
            return false;
        }

        GlStateManager._disableCull();
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._disableBlend();

        stateCache.activeTexture(GL13.GL_TEXTURE0 + 2);
        bindTextureView(lightmapTexture);
        stateCache.bindSampler(2, clampSamplerId);

        stateCache.activeTexture(GL13.GL_TEXTURE0 + 1);
        bindTextureView(overlayTexture);
        stateCache.bindSampler(1, clampSamplerId);

        stateCache.activeTexture(GL13.GL_TEXTURE0);
        bindTextureView(modelTexture);
        stateCache.bindSampler(0, modelSamplerId);

        stateCache.bindSsbo(mesh.boneSsbo);
        GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, boneBuf);
        stateCache.bindSsboBase(BoneSkinShader.ssbo, mesh.boneSsbo);

        float fogStart = 0f;
        float fogEnd = 1f;
        float[] fogColor = fogColorScratch;
        int fogShape = 0; // MC 26.x: getShaderFogShape() return type changed

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

        int alphaMode = 1;
        if (BoneSkinShader.locAlphaMode() >= 0) GL20.glUniform1i(BoneSkinShader.locAlphaMode(), alphaMode);
        GpuDebugLog.verbose("frame={} draw texture={} translucent={} alphaMode={} partMask={} drawCount={} part3Extra={} vertices={} indices={} proj00={} proj11={} proj22={} root30={} root31={} root32={}",
                frameId, textureLocation, translucentTexture, alphaMode, renderPartMask, drawCount,
                (renderPartMask == 1 || renderPartMask == 2) ? mesh.partMask3Count : 0,
                mesh.vertexCount, mesh.indexCount,
                projScratch[0], projScratch[5], projScratch[10],
                rootPose.m30(), rootPose.m31(), rootPose.m32());
        drawMeshParts(mesh, renderPartMask);
        GpuDebugLog.glError("draw frame=" + frameId);

        stateCache.bindSsboBase(BoneSkinShader.ssbo, 0);
        stateCache.bindSsbo(0);
        stateCache.useProgram(0);

        GlStateManager._glBindVertexArray(0);
        restoreRenderState();
        GpuDebugLog.glError("restore frame=" + frameId);

        return true;
    }

    private static void restoreRenderState() {
        stateCache.restoreTextureParameters();
        GL33.glBindSampler(0, 0);
        GL33.glBindSampler(1, 0);
        GL33.glBindSampler(2, 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._disableBlend();
        GlStateManager._enableCull();
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
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
        currentLights[0] = light0Scratch;
        currentLights[1] = light1Scratch;
    }

    private static boolean computeBoneMatricesNative(
            GeoModel model,
            GpuMesh mesh,
            Matrix4f rootPose,
            Matrix3f rootNormal,
            float[] boneParams,
            float[] stateBuffer,
            int packedLight,
            ByteBuffer out
    ) {
        int boneCount = model.bakedBones.size();
        if (mesh.pointer == 0 || boneParams == null || boneParams.length < boneCount * 12) {
            return false;
        }

        rootPose.get(rootPoseScratch);
        rootNormal.get(rootNormalScratch);
        updatePivotAbsStateBuffer(model, boneParams, stateBuffer);

        try {
            GeoModel.nComputeBoneMatrices(mesh.pointer, rootPoseScratch, rootNormalScratch, boneParams, packedLight, out);
            return true;
        } catch (Throwable t) {
            GpuDebugLog.error("native bone matrix computation threw", t);
            return false;
        }
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

    private static TextureBinding resolveTextureBinding(AbstractTexture texture) {
        if (texture == null) {
            return TextureBinding.EMPTY;
        }
        try {
            return resolveTextureBinding(texture.getTextureView());
        } catch (RuntimeException ignored) {
            return TextureBinding.EMPTY;
        }
    }

    private static TextureBinding resolveTextureBinding(GpuTextureView textureView) {
        if (!(textureView instanceof GlTextureView glTextureView)) {
            return TextureBinding.EMPTY;
        }
        try {
            GlTexture glTexture = glTextureView.texture();
            return new TextureBinding(glTexture.glId(), textureView.baseMipLevel(), textureView.mipLevels());
        } catch (RuntimeException ignored) {
            return TextureBinding.EMPTY;
        }
    }

    private static void bindTextureView(TextureBinding texture) {
        stateCache.bindTexture(texture);
    }

    private static int resolveSamplerId(GpuSampler sampler) {
        if (!(sampler instanceof GlSampler glSampler)) {
            return 0;
        }
        try {
            return glSampler.getId();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static TextureBinding resolveOverlayTexture(OverlayTexture overlayTexture) {
        if (overlayTexture == null) {
            return TextureBinding.EMPTY;
        }
        try {
            return resolveTextureBinding(overlayTexture.getTextureView());
        } catch (RuntimeException ignored) {
            return TextureBinding.EMPTY;
        }
    }

    private static TextureBinding resolveLightmapTexture(Minecraft mc) {
        try {
            return resolveTextureBinding(mc.gameRenderer.lightmap());
        } catch (RuntimeException ignored) {
            return TextureBinding.EMPTY;
        }
    }

    private static int resolveClampSamplerId() {
        try {
            return resolveSamplerId(RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public static void disposeMesh(GeoModel model) {
        if (model.gpuMeshHandle == 0) return;
        GpuMesh mesh = meshMap.remove(model.gpuMeshHandle);
        if (mesh != null) mesh.dispose();
        model.gpuMeshHandle = 0;
    }

    public static void disposeAllMeshes(String reason) {
        if (!RenderSystem.isOnRenderThread()) {
            ((Executor) Minecraft.getInstance()).execute(() -> disposeAllMeshes(reason));
            return;
        }
        int releasedMeshes = 0;
        long releasedBytes = 0L;
        for (Map.Entry<Long, GpuMesh> entry : meshMap.entrySet()) {
            GpuMesh mesh = entry.getValue();
            if (mesh != null && meshMap.remove(entry.getKey(), mesh)) {
                releasedMeshes++;
                releasedBytes += mesh.estimatedBytes;
                mesh.dispose();
            }
        }
        if (releasedMeshes > 0) {
            GpuDebugLog.info("disposed all GPU meshes reason={} releasedMeshes={} estimatedReleasedBytes={}",
                    reason, releasedMeshes, releasedBytes);
        }
    }

    public static GpuMesh getOrBuildMesh(GeoModel model) {
        if (model.gpuMeshHandle == 0) {
            GpuMesh mesh = GpuMeshBuilder.build(model);
            if (mesh == null) return null;
            model.gpuMeshHandle = encodeMeshRef(mesh);
        }
        return decodeMeshRef(model.gpuMeshHandle);
    }

    private static long encodeMeshRef(GpuMesh mesh) {
        long ref = GpuRenderPath.ref.getAndIncrement();
        meshMap.put(ref, mesh);
        return ref;
    }

    private static GpuMesh decodeMeshRef(long ref) {
        return meshMap.get(ref);
    }

    private static final class RenderStateCache {
        private final int[] textureIds = new int[8];
        private final int[] samplerIds = new int[8];
        private final int[] ssboBases = new int[8];
        private final int[] savedTextureIds = new int[16];
        private final int[] savedTextureBaseMip = new int[16];
        private final int[] savedTextureMaxMip = new int[16];
        private int activeTexture = -1;
        private int ssbo = -1;
        private int program = -1;
        private int savedTextureCount = 0;

        void invalidate() {
            Arrays.fill(textureIds, Integer.MIN_VALUE);
            Arrays.fill(samplerIds, Integer.MIN_VALUE);
            Arrays.fill(ssboBases, Integer.MIN_VALUE);
            activeTexture = -1;
            ssbo = -1;
            program = -1;
            savedTextureCount = 0;
        }

        void useProgram(int id) {
            if (program == id) {
                return;
            }
            GlStateManager._glUseProgram(id);
            program = id;
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

        void bindTexture(TextureBinding texture) {
            int unit = Math.max(0, activeTexture - GL13.GL_TEXTURE0);
            if (unit >= textureIds.length) {
                GlStateManager._bindTexture(texture.id);
                applyTextureView(texture);
                return;
            }
            if (textureIds[unit] != texture.id) {
                GlStateManager._bindTexture(texture.id);
                textureIds[unit] = texture.id;
            }
            applyTextureView(texture);
        }

        void activeTexture(int texture) {
            if (activeTexture == texture) {
                return;
            }
            GlStateManager._activeTexture(texture);
            activeTexture = texture;
        }

        void bindSampler(int unit, int sampler) {
            if (unit >= 0 && unit < samplerIds.length && samplerIds[unit] == sampler) {
                return;
            }
            GL33.glBindSampler(unit, sampler);
            if (unit >= 0 && unit < samplerIds.length) {
                samplerIds[unit] = sampler;
            }
        }

        private void applyTextureView(TextureBinding texture) {
            rememberTextureParameters(texture.id);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, texture.baseMipLevel);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, texture.maxMipLevel());
        }

        private void rememberTextureParameters(int textureId) {
            if (textureId == 0) {
                return;
            }
            for (int i = 0; i < savedTextureCount; i++) {
                if (savedTextureIds[i] == textureId) {
                    return;
                }
            }
            if (savedTextureCount >= savedTextureIds.length) {
                return;
            }
            savedTextureIds[savedTextureCount] = textureId;
            savedTextureBaseMip[savedTextureCount] = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL);
            savedTextureMaxMip[savedTextureCount] = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL);
            savedTextureCount++;
        }

        void restoreTextureParameters() {
            if (savedTextureCount <= 0) {
                return;
            }
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            for (int i = 0; i < savedTextureCount; i++) {
                GlStateManager._bindTexture(savedTextureIds[i]);
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, savedTextureBaseMip[i]);
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, savedTextureMaxMip[i]);
                savedTextureIds[i] = 0;
            }
            savedTextureCount = 0;
        }
    }

    private static final class TextureBinding {
        private static final TextureBinding EMPTY = new TextureBinding(0, 0, 0);

        final int id;
        final int baseMipLevel;
        final int mipLevels;

        TextureBinding(int id, int baseMipLevel, int mipLevels) {
            this.id = id;
            this.baseMipLevel = baseMipLevel;
            this.mipLevels = mipLevels;
        }

        boolean isValid() {
            return id != 0 && mipLevels > 0;
        }

        int maxMipLevel() {
            return baseMipLevel + mipLevels - 1;
        }
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
}
