

package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.micaftic.morpher.NativeLibLoader;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.SubmitRenderContext;
import com.micaftic.morpher.config.GeneralConfig;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.util.log.ChatLogger;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import com.micaftic.morpher.core.compat.oculus.OculusCompat;
import com.micaftic.morpher.core.compat.optifine.OptiFineDetector;
import com.micaftic.morpher.core.gpu.GpuCapability;
import com.micaftic.morpher.core.gpu.GpuDebugLog;
import com.micaftic.morpher.core.gpu.GpuRenderPath;
import com.micaftic.morpher.core.gpu.IrisRenderPath;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

public class NativeModelRenderer {
    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;

    private static final Matrix4f projectionModelViewMatrix = new Matrix4f();
    private static final ThreadLocal<RenderScratch> FALLBACK_SCRATCH = ThreadLocal.withInitial(RenderScratch::new);

    public static void renderMesh(VertexConsumer buffer, PoseStack.Pose pose, GeoModel model, float[] boneParams, float[] stateBuffer, int textureIndex, int renderPartMask, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        renderMesh(buffer, pose, model, boneParams, stateBuffer, textureIndex, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha, null);
    }

    public static void renderMesh(VertexConsumer buffer, PoseStack.Pose pose, GeoModel model, float[] boneParams, float[] stateBuffer, int textureIndex, int renderPartMask, int packedLight, int packedOverlay, float red, float green, float blue, float alpha, net.minecraft.resources.Identifier textureLocation) {
        renderMesh(buffer, pose, model, boneParams, stateBuffer, textureIndex, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha, textureLocation, true);
    }

    public static void renderMesh(VertexConsumer buffer, PoseStack.Pose pose, GeoModel model, float[] boneParams, float[] stateBuffer, int textureIndex, int renderPartMask, int packedLight, int packedOverlay, float red, float green, float blue, float alpha, net.minecraft.resources.Identifier textureLocation, boolean allowDirectGpuRenderer) {
        OculusCompat.updatePBRState();
        projectionModelViewMatrix.identity();
        boolean isPreview = ModelPreviewRenderer.isPreview() || ModelPreviewRenderer.isExtraPlayer();
        boolean shaderPackInUse = OculusCompat.isShaderPackInUse() && !isPreview;
        boolean disableGlow = shouldDisableModelGlow(shaderPackInUse);

        // Submit-based world renders must keep the normal geometry path so the
        // entity still reaches the feature/shadow pipeline.
        boolean translucentTexture = model.isTranslucentTexture(textureIndex);
        boolean useGpuRenderer = allowDirectGpuRenderer && !translucentTexture && !disableGlow && textureLocation != null && SubmitRenderContext.get() == null && ModelPreviewRenderer.isWorldRender() && !isPreview && !ModelPreviewRenderer.isFirstPerson() && NativeLibLoader.isLoaded() && !GeneralConfig.USE_COMPATIBILITY_RENDERER.get() && GeneralConfig.USE_GPU_RENDERER.get();
        boolean useNativeSimdRenderer = GeneralConfig.safeGet(GeneralConfig.USE_NATIVE_SIMD_RENDERER, false);
        GpuDebugLog.verbose("entry texture={} allowGpu={} useGpu={} translucent={} disableGlow={} shaderPack={} preview={} firstPerson={} submitContext={} worldRender={} compat={} gpuCfg={} nativeSimdCfg={} nativeLoaded={}",
                textureLocation, allowDirectGpuRenderer, useGpuRenderer, translucentTexture, disableGlow, shaderPackInUse,
                isPreview, ModelPreviewRenderer.isFirstPerson(), SubmitRenderContext.get() != null, ModelPreviewRenderer.isWorldRender(),
                GeneralConfig.USE_COMPATIBILITY_RENDERER.get(), GeneralConfig.USE_GPU_RENDERER.get(), useNativeSimdRenderer, NativeLibLoader.isLoaded());
        if (useGpuRenderer) {
            if (!GpuCapability.isAvailable()) {
                ChatLogger.INSTANCE.logFormatted("Disabled GPU renderer for: " + GpuCapability.getReason());
                GeneralConfig.USE_GPU_RENDERER.set(false);
                GeneralConfig.USE_GPU_RENDERER.save();
            } else if (shaderPackInUse) {
                if (IrisRenderPath.tryRender(model, pose, boneParams, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha, textureLocation)) {
                    GpuDebugLog.verbose("entry rendered through IrisRenderPath texture={}", textureLocation);
                    return;
                }
                GpuDebugLog.verbose("entry IrisRenderPath fallback texture={}", textureLocation);
            } else {
                if (GpuRenderPath.tryRender(model, pose, boneParams, stateBuffer, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha, textureLocation, translucentTexture)) {
                    GpuDebugLog.verbose("entry rendered through GpuRenderPath texture={}", textureLocation);
                    return;
                }
                GpuDebugLog.verbose("entry GpuRenderPath fallback texture={}", textureLocation);
            }
        }

        if (useNativeSimdRenderer && !isPreview && !ModelPreviewRenderer.isFirstPerson() && !translucentTexture && NativeLibLoader.isLoaded() && !GeneralConfig.USE_COMPATIBILITY_RENDERER.get() && !disableGlow) { // WIP: SIMD MODEL RENDER
            GpuDebugLog.verbose("entry rendered through native SIMD texture={} partMask={}", textureLocation, renderPartMask);
            nativeRenderModel(
                    buffer,
                    pose,
                    projectionModelViewMatrix,
                    OptiFineDetector.isOptifinePresent(),
                    model,
                    boneParams,
                    stateBuffer,
                    textureIndex,
                    renderPartMask,
                    packedLight,
                    packedOverlay,
                    red, green, blue, alpha,
                    isPreview
            );
        } else {
            GpuDebugLog.verbose("entry rendered through Java model path texture={} nativeSimdCfg={} translucent={} preview={} firstPerson={} compat={} disableGlow={}",
                    textureLocation, useNativeSimdRenderer, translucentTexture, isPreview, ModelPreviewRenderer.isFirstPerson(),
                    GeneralConfig.USE_COMPATIBILITY_RENDERER.get(), disableGlow);
            renderModel(
                    buffer,
                    pose,
                    projectionModelViewMatrix,
                    OptiFineDetector.isOptifinePresent(),
                    model,
                    boneParams,
                    stateBuffer,
                    textureIndex,
                    renderPartMask,
                    packedLight,
                    packedOverlay,
                    red, green, blue, alpha,
                    isPreview,
                    disableGlow
            );
        }
    }

    private static boolean shouldDisableModelGlow(boolean shaderPackInUse) {
        return shaderPackInUse && GeneralConfig.safeGet(GeneralConfig.DISABLE_MODEL_GLOW_IN_SHADERPACK, true);
    }

    public static void renderModel(
            VertexConsumer vertexConsumer,
            PoseStack.Pose pose,
            Matrix4f projectionModelViewMatrix,
            boolean isCompatMode,
            GeoModel mesh,
            float[] boneParams,
            float[] stateBuffer,
            int textureIndex, int renderPartMask,
            int packedLight, int packedOverlay,
            float r, float g, float b, float a,
            boolean isPreview) {
        renderModel(
                vertexConsumer,
                pose,
                projectionModelViewMatrix,
                isCompatMode,
                mesh,
                boneParams,
                stateBuffer,
                textureIndex,
                renderPartMask,
                packedLight,
                packedOverlay,
                r, g, b, a,
                isPreview,
                false
        );
    }

    public static void renderModel(
            VertexConsumer vertexConsumer,
            PoseStack.Pose pose,
            Matrix4f projectionModelViewMatrix,
            boolean isCompatMode,
            GeoModel mesh,
            float[] boneParams,
            float[] stateBuffer,
            int textureIndex, int renderPartMask,
            int packedLight, int packedOverlay,
            float r, float g, float b, float a,
            boolean isPreview,
            boolean disableGlow) {

        if (mesh.bakedBones == null || mesh.bakedBones.isEmpty()) return;

        // TODO: 淇京GC澹撳姏
        RenderScratch scratch = FALLBACK_SCRATCH.get();
        scratch.ensureBoneCapacity(mesh.bakedBones.size());

        Matrix4f rootPoseMat = pose.pose();
        Matrix3f rootNormalMC = pose.normal();
        Matrix4f identityMat = scratch.identityMat.identity();
        Matrix4f globalBoneMat = scratch.globalBoneMat;
        Matrix4f projBoneMat = scratch.projBoneMat;
        Matrix3f localNormalMat = scratch.localNormalMat;
        Matrix3f globalNormalMat = scratch.globalNormalMat;
        Vector4f tempPos = scratch.tempPos;
        Vector3f tempNorm = scratch.tempNorm;
        Matrix4f[] boneLocalTransforms = scratch.boneLocalTransforms;
        boolean[] boneVisible = scratch.boneVisible;
        boolean[] boneComputed = scratch.boneComputed;
        Arrays.fill(boneComputed, 0, mesh.bakedBones.size(), false);

        for (int i = 0; i < mesh.bakedBones.size(); i++) {
            calculateBoneMatrix(i, mesh.bakedBones, boneParams, boneLocalTransforms, boneVisible, boneComputed, identityMat, stateBuffer);
        }

        for (int i = 0; i < mesh.bakedBones.size(); i++) {
            if (!boneVisible[i]) {
                continue;
            }

            GeoModel.BakedBone bone = mesh.bakedBones.get(i);
            if (renderPartMask != 0 && bone.partMask != renderPartMask && bone.partMask != 3) {
                continue;
            }

            Matrix4f localBoneMat = boneLocalTransforms[i];
            globalBoneMat.set(rootPoseMat).mul(localBoneMat);
            projBoneMat.identity().mul(globalBoneMat);

            // 娉曠窔鍏ㄥ煙鐭╅櫍
            localBoneMat.normal(localNormalMat);
            globalNormalMat.set(rootNormalMC).mul(localNormalMat);

            int currentPackedLight = bone.glow && !disableGlow ? FULL_BRIGHT_LIGHT : packedLight;

            for (GeoModel.BakedCube cube : bone.cubes) {
                for (GeoModel.BakedQuad quad : cube.quads) {
                    tempNorm.set(quad.normal).mul(globalNormalMat).normalize();
                    for (int v = 0; v < 4; v++) {
                        tempPos.set(quad.positions[v].x(), quad.positions[v].y(), quad.positions[v].z(), 1.0f).mul(globalBoneMat);
                        vertexConsumer.addVertex(tempPos.x(), tempPos.y(), tempPos.z(), ((int)(a * 255) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255), quad.uvs[v].x(), quad.uvs[v].y(), packedOverlay, currentPackedLight, tempNorm.x(), tempNorm.y(), tempNorm.z());
                    }
                }
            }
        }
    }

    private static Matrix4f calculateBoneMatrix(int idx, java.util.List<GeoModel.BakedBone> bones, float[] boneParams, Matrix4f[] cache, boolean[] visibleCache, boolean[] computedCache, Matrix4f rootPose, float[] stateBuffer) {
        if (computedCache[idx]) return cache[idx];

        GeoModel.BakedBone bone = bones.get(idx);
        Matrix4f parentMatrix = rootPose;
        boolean isVisible = true;

        if (bone.parentIdx != -1) {
            parentMatrix = calculateBoneMatrix(bone.parentIdx, bones, boneParams, cache, visibleCache, computedCache, rootPose, stateBuffer);
            // If parent bone is not visible, child bone must also not be visible
            if (!visibleCache[bone.parentIdx]) {
                isVisible = false;
            }
        }

        Matrix4f localMat = cache[idx];
        if (localMat == null) {
            localMat = new Matrix4f();
            cache[idx] = localMat;
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

        float unk1 = boneParams[pOffset + 9];
        float unk2 = boneParams[pOffset + 10];
        float unk3 = boneParams[pOffset + 11];

        if (unk1 != 0.0F && unk2 != 0.0F && unk3 != 0.0F) {
            //"".hashCode();
        }

        if (animSx == 0.0f && animSy == 0.0f && animSz == 0.0f) {
            isVisible = false;
        }/* else if (unk1 == 1 || unk2 == 1) isVisible = false;*/

        localMat.translate(
                (bone.pivotX - animTx) * 0.0625f,
                (bone.pivotY + animTy) * 0.0625f,
                (bone.pivotZ + animTz) * 0.0625f
        );
        localMat.rotateZ(animRz);
        localMat.rotateY(animRy);
        localMat.rotateX(animRx);

//        if (bone.name.equals("gun")) {
//            //"".hashCode();
//        }

        if (animSx != 1.0f || animSy != 1.0f || animSz != 1.0f) {
            localMat.scale(animSx, animSy, animSz);
        }

        if (unk3 == 1.0F && stateBuffer != null && isVisible) {
            int offset = idx * 4;
            // bone pivot abs
            if (offset + 2 < stateBuffer.length) {
                stateBuffer[offset + 0] =-localMat.m30() * 16;
                stateBuffer[offset + 1] = localMat.m31() * 16;
                stateBuffer[offset + 2] = localMat.m32() * 16;
            }
        }

        localMat.translate(-bone.pivotX / 16f, -bone.pivotY / 16f, -bone.pivotZ / 16f);

        cache[idx] = localMat;
        visibleCache[idx] = isVisible;
        computedCache[idx] = true;
        return localMat;
    }

    private static final float[] matrixTransferArray = new float[48];
    @SuppressWarnings("unused") // TODO: native writes vertices directly to VertexConsumer buffer
    public static void submitVertices(Object v, int vertexCount, ByteBuffer fBuf, ByteBuffer iBuf) {
        FloatBuffer f = fBuf.order(ByteOrder.nativeOrder()).asFloatBuffer();
        IntBuffer in = iBuf.order(ByteOrder.nativeOrder()).asIntBuffer();
        VertexConsumer vc = (VertexConsumer) v;
        int fIdx = 0, iIdx = 0;
        for (int n = 0; n < vertexCount; n++) {
            int packedColor = ((int)(f.get(fIdx + 6) * 255) << 24) | ((int)(f.get(fIdx + 3) * 255) << 16) | ((int)(f.get(fIdx + 4) * 255) << 8) | (int)(f.get(fIdx + 5) * 255);
            vc.addVertex(
                    f.get(fIdx),     f.get(fIdx + 1), f.get(fIdx + 2),
                    packedColor,
                    f.get(fIdx + 7), f.get(fIdx + 8),
                    in.get(iIdx),    in.get(iIdx + 1),
                    f.get(fIdx + 9), f.get(fIdx + 10), f.get(fIdx + 11)
            );
            fIdx += 12;
            iIdx += 2;
        }
    }


    public static void nativeRenderModel( // TODO:
            VertexConsumer vertexConsumer, PoseStack.Pose pose, Matrix4f projectionModelViewMatrix,
            boolean isCompatMode, GeoModel mesh, float[] boneVertex, float[] stateBuffer,
            int textureIndex, int renderPartMask, int packedLight, int packedOverlay,
            float r, float g, float b, float a, boolean isPreview) {

        if (mesh.nativeModelHandle == 0) return;

        pose.pose().get(matrixTransferArray, 0);
        pose.normal().get(matrixTransferArray, 16);
        projectionModelViewMatrix.identity().get(matrixTransferArray, 32);

        GeoModel.nComputeModelVertices(
                mesh.nativeModelHandle,
                vertexConsumer,
                matrixTransferArray,
                boneVertex,
                renderPartMask,
                packedLight, packedOverlay,
                r, g, b, a
        );
    }

    private static final class RenderScratch {
        final Matrix4f identityMat = new Matrix4f();
        final Matrix4f globalBoneMat = new Matrix4f();
        final Matrix4f projBoneMat = new Matrix4f();
        final Matrix3f localNormalMat = new Matrix3f();
        final Matrix3f globalNormalMat = new Matrix3f();
        final Vector4f tempPos = new Vector4f();
        final Vector3f tempNorm = new Vector3f();
        Matrix4f[] boneLocalTransforms = new Matrix4f[0];
        boolean[] boneVisible = new boolean[0];
        boolean[] boneComputed = new boolean[0];

        void ensureBoneCapacity(int boneCount) {
            if (boneLocalTransforms.length < boneCount) {
                boneLocalTransforms = Arrays.copyOf(boneLocalTransforms, boneCount);
                boneVisible = Arrays.copyOf(boneVisible, boneCount);
                boneComputed = Arrays.copyOf(boneComputed, boneCount);
            }
        }
    }
}
