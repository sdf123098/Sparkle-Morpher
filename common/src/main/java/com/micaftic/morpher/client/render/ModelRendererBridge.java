package com.micaftic.morpher.client.render;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.core.acceleration.AccelerationCapability;
import com.micaftic.morpher.core.compat.oculus.OculusCompat;
import com.micaftic.morpher.core.compat.optifine.OptiFineDetector;
import com.micaftic.morpher.core.config.ConfigPolicies;
import com.micaftic.morpher.core.gpu.GpuCapability;
import com.micaftic.morpher.core.render.RenderBackend;
import com.micaftic.morpher.core.vector.JdkVectorModelMath;
import com.micaftic.morpher.core.vector.VectorApiCapability;
import com.micaftic.morpher.util.log.ChatLogger;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.spm.core.acceleration.CpuComputeProvider;
import dev.spm.core.api.BoneMatrices;
import dev.spm.core.api.BonePose;
import dev.spm.core.api.VertexBatch;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates model rendering across OpenGL GPU, {@code spm-core} Rust SIMD, and pure-Java backends.
 */
public class ModelRendererBridge {
    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;
    private static final boolean FORCE_CORE_NATIVE_RIGID = Boolean.getBoolean("spm.force.core.native.rigid");
    private static final AtomicBoolean LOGGED_NATIVE_RIGID_BYPASS = new AtomicBoolean(false);

    public enum BoneRenderPass {
        ALL,
        NON_GLOW,
        GLOW
    }

    private static final Matrix4f projectionModelViewMatrix = new Matrix4f();
    private static final ThreadLocal<RenderScratch> FALLBACK_SCRATCH = ThreadLocal.withInitial(RenderScratch::new);
    private static final ThreadLocal<int[]> PREVIEW_BACKEND_COUNTS = ThreadLocal.withInitial(() -> new int[2]);
    private static final AtomicBoolean SIMD_PREVIEW_FALLBACK_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean NATIVE_CACHE_FAIL_LOGGED = new AtomicBoolean(false);

    public static void beginPreviewFrame() {
        int[] counts = PREVIEW_BACKEND_COUNTS.get();
        counts[0] = 0;
        counts[1] = 0;
    }

    public static boolean wasPreviewGpuRendered() {
        int[] counts = PREVIEW_BACKEND_COUNTS.get();
        return counts[0] > 0 && counts[1] == 0;
    }

    public static int getPreviewGpuPassCount() {
        return PREVIEW_BACKEND_COUNTS.get()[0];
    }

    public static int getPreviewFallbackPassCount() {
        return PREVIEW_BACKEND_COUNTS.get()[1];
    }

    private static boolean canUsePreviewGpu(BoneRenderPass boneRenderPass,
                                             net.minecraft.resources.ResourceLocation textureLocation) {
        return boneRenderPass == BoneRenderPass.ALL
                && textureLocation != null
                && AccelerationCapability.canBuildGpuMesh()
                && !ConfigPolicies.render().useCompatibilityRenderer()
                && ConfigPolicies.render().useGpuRenderer()
                && !OculusCompat.isShaderPackInUse()
                && GpuCapability.isAvailable();
    }

    public static void renderMesh(VertexConsumer buffer, PoseStack.Pose pose, BuiltModel model, float[] boneParams, float[] stateBuffer, int textureIndex, int renderPartMask, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        renderMesh(buffer, pose, model, boneParams, stateBuffer, textureIndex, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha, null);
    }

    public static void renderMesh(VertexConsumer buffer, PoseStack.Pose pose, BuiltModel model, float[] boneParams, float[] stateBuffer, int textureIndex, int renderPartMask, int packedLight, int packedOverlay, float red, float green, float blue, float alpha, net.minecraft.resources.ResourceLocation textureLocation) {
        renderMeshPass(buffer, pose, model, boneParams, stateBuffer, textureIndex, renderPartMask,
                packedLight, packedOverlay, red, green, blue, alpha, textureLocation, BoneRenderPass.ALL);
    }

    public static void renderMeshPass(VertexConsumer buffer, PoseStack.Pose pose, BuiltModel model,
            float[] boneParams, float[] stateBuffer, int textureIndex, int renderPartMask,
            int packedLight, int packedOverlay, float red, float green, float blue, float alpha,
            net.minecraft.resources.ResourceLocation textureLocation, BoneRenderPass boneRenderPass) {
        OculusCompat.updatePBRState();
        RenderSystem.getProjectionMatrix().mul(RenderSystem.getModelViewMatrix(), projectionModelViewMatrix);
        boolean isPreview = ModelPreviewRenderer.isPreview() || RenderContext.isGuiPreview();

        if (isPreview) {
            RenderBackend.RenderRequest previewRequest = new RenderBackend.RenderRequest(
                    buffer, pose, projectionModelViewMatrix, model, boneParams, stateBuffer,
                    textureIndex, renderPartMask, FULL_BRIGHT_LIGHT, packedOverlay,
                    red, green, blue, alpha, textureLocation, model.isTranslucentTexture(textureIndex),
                    true, true, boneRenderPass);
            if (canUsePreviewGpu(boneRenderPass, textureLocation)
                    && RenderBackends.gpu().tryRender(previewRequest)) {
                PREVIEW_BACKEND_COUNTS.get()[0]++;
                return;
            }
            PREVIEW_BACKEND_COUNTS.get()[1]++;
            com.micaftic.morpher.core.render.NativeSimdPolicy previewNativePolicy = ConfigPolicies.graphics().nativeSimdPolicy();
            if (boneRenderPass == BoneRenderPass.ALL
                    && previewNativePolicy == com.micaftic.morpher.core.render.NativeSimdPolicy.AGGRESSIVE
                    && AccelerationCapability.canRenderSimd()
                    && !ConfigPolicies.render().useCompatibilityRenderer()) {
                if (RenderBackends.simd().tryRender(previewRequest)) {
                    return;
                }
                logPreviewSimdFallbackOnce();
            }
            renderModel(
                    buffer, pose, projectionModelViewMatrix, OptiFineDetector.isOptifinePresent(),
                    model, boneParams, stateBuffer, textureIndex, renderPartMask,
                    FULL_BRIGHT_LIGHT, packedOverlay, red, green, blue, alpha, true, boneRenderPass
            );
            return;
        }

        boolean useGpuRenderer = !isPreview
                && boneRenderPass == BoneRenderPass.ALL
                && textureLocation != null
                && AccelerationCapability.canBuildGpuMesh()
                && !ConfigPolicies.render().useCompatibilityRenderer()
                && ConfigPolicies.render().useGpuRenderer()
                && !OculusCompat.isShaderPackInUse();

        RenderBackend.RenderRequest request = new RenderBackend.RenderRequest(
                buffer, pose, projectionModelViewMatrix, model, boneParams, stateBuffer,
                textureIndex, renderPartMask, packedLight, packedOverlay,
                red, green, blue, alpha, textureLocation, model.isTranslucentTexture(textureIndex),
                isPreview, OptiFineDetector.isOptifinePresent(), boneRenderPass);
        if (useGpuRenderer) {
            if (!GpuCapability.isAvailable()) {
                ChatLogger.INSTANCE.logFormatted("Disabled GPU renderer for: " + GpuCapability.getReason());
                GeneralConfig.USE_GPU_RENDERER.set(false);
                return;
            }
            if (RenderBackends.gpu().tryRender(request)) {
                return;
            }
        }
        com.micaftic.morpher.core.render.NativeSimdPolicy nativePolicy = ConfigPolicies.graphics().nativeSimdPolicy();
        if (nativePolicy != com.micaftic.morpher.core.render.NativeSimdPolicy.OFF
                && boneRenderPass == BoneRenderPass.ALL
                && AccelerationCapability.canRenderSimd() && !ConfigPolicies.render().useCompatibilityRenderer()) {
            if (RenderBackends.simd().tryRender(request)) {
                return;
            }
        }
        RenderBackends.java().tryRender(request);
    }

    public static void renderModel(
            VertexConsumer vertexConsumer, PoseStack.Pose pose, Matrix4f projectionModelViewMatrix,
            boolean isCompatMode, BuiltModel mesh, float[] boneParams, float[] stateBuffer,
            int textureIndex, int renderPartMask, int packedLight, int packedOverlay,
            float r, float g, float b, float a, boolean isPreview) {
        renderModel(vertexConsumer, pose, projectionModelViewMatrix, isCompatMode, mesh,
                boneParams, stateBuffer, textureIndex, renderPartMask, packedLight, packedOverlay,
                r, g, b, a, isPreview, BoneRenderPass.ALL);
    }

    public static void renderModel(
            VertexConsumer vertexConsumer, PoseStack.Pose pose, Matrix4f projectionModelViewMatrix,
            boolean isCompatMode, BuiltModel mesh, float[] boneParams, float[] stateBuffer,
            int textureIndex, int renderPartMask, int packedLight, int packedOverlay,
            float r, float g, float b, float a, boolean isPreview, BoneRenderPass boneRenderPass) {

        if (mesh.bakedBones == null || mesh.bakedBones.isEmpty()) return;
        int boneCount = mesh.bakedBones.size();
        RenderScratch scratch = FALLBACK_SCRATCH.get();
        boolean javaVectorRequested = ConfigPolicies.graphics().experimentalJavaVectorRenderer();
        VectorApiCapability.warnIfRequested(javaVectorRequested);
        boolean useJavaVector = javaVectorRequested && VectorApiCapability.isAvailable();

        com.micaftic.morpher.core.gpu.SpmCoreBoneMatrixWriter.EvaluatedBones evaluated =
                com.micaftic.morpher.core.gpu.SpmCoreBoneMatrixWriter.evaluateForCpuRender(
                        mesh, boneParams, stateBuffer);
        if (evaluated == null) {
            return;
        }
        float[] transforms = evaluated.transforms;
        float[] normals = evaluated.normals;
        boolean[] boneVisible = evaluated.visible;

        int[] renderBoneOrder = mesh.getPartMaskBoneRenderOrder(renderPartMask);
        if (renderBoneOrder == null || renderBoneOrder.length == 0) {
            renderBoneOrder = scratch.fallbackRenderOrder(boneCount);
        }

        Matrix4f rootPoseMat = pose.pose();
        Matrix3f rootNormalMC = pose.normal();
        float r00 = rootPoseMat.m00(), r01 = rootPoseMat.m01(), r02 = rootPoseMat.m02(), r03 = rootPoseMat.m03();
        float r10 = rootPoseMat.m10(), r11 = rootPoseMat.m11(), r12 = rootPoseMat.m12(), r13 = rootPoseMat.m13();
        float r20 = rootPoseMat.m20(), r21 = rootPoseMat.m21(), r22 = rootPoseMat.m22(), r23 = rootPoseMat.m23();
        float r30 = rootPoseMat.m30(), r31 = rootPoseMat.m31(), r32 = rootPoseMat.m32(), r33 = rootPoseMat.m33();
        boolean affineRoot = r03 == 0.0f && r13 == 0.0f && r23 == 0.0f && r33 == 1.0f;

        float rn00 = rootNormalMC.m00(), rn01 = rootNormalMC.m01(), rn02 = rootNormalMC.m02();
        float rn10 = rootNormalMC.m10(), rn11 = rootNormalMC.m11(), rn12 = rootNormalMC.m12();
        float rn20 = rootNormalMC.m20(), rn21 = rootNormalMC.m21(), rn22 = rootNormalMC.m22();

        float[] vectorX = scratch.vectorX;
        float[] vectorY = scratch.vectorY;
        float[] vectorZ = scratch.vectorZ;
        int packedColor = packArgb(a, r, g, b);

        for (int orderIndex = 0; orderIndex < renderBoneOrder.length; orderIndex++) {
            int i = renderBoneOrder[orderIndex];
            if (i < 0 || i >= boneCount || !boneVisible[i]) {
                continue;
            }
            BuiltModel.BakedBone bone = mesh.bakedBones.get(i);
            if (boneRenderPass == BoneRenderPass.GLOW && !bone.glow
                    || boneRenderPass == BoneRenderPass.NON_GLOW && bone.glow) {
                continue;
            }
            if (renderPartMask != 0 && bone.partMask != renderPartMask && bone.partMask != 3) {
                continue;
            }
            if (bone.cubes.isEmpty()) {
                continue;
            }

            int mOff = i * 16;
            float l00 = transforms[mOff],     l01 = transforms[mOff + 1],  l02 = transforms[mOff + 2];
            float l10 = transforms[mOff + 4], l11 = transforms[mOff + 5],  l12 = transforms[mOff + 6];
            float l20 = transforms[mOff + 8], l21 = transforms[mOff + 9],  l22 = transforms[mOff + 10];
            float l30 = transforms[mOff + 12], l31 = transforms[mOff + 13], l32 = transforms[mOff + 14];

            float m00 = r00 * l00 + r10 * l01 + r20 * l02;
            float m01 = r01 * l00 + r11 * l01 + r21 * l02;
            float m02 = r02 * l00 + r12 * l01 + r22 * l02;

            float m10 = r00 * l10 + r10 * l11 + r20 * l12;
            float m11 = r01 * l10 + r11 * l11 + r21 * l12;
            float m12 = r02 * l10 + r12 * l11 + r22 * l12;

            float m20 = r00 * l20 + r10 * l21 + r20 * l22;
            float m21 = r01 * l20 + r11 * l21 + r21 * l22;
            float m22 = r02 * l20 + r12 * l21 + r22 * l22;

            float m30 = r00 * l30 + r10 * l31 + r20 * l32 + r30;
            float m31 = r01 * l30 + r11 * l31 + r21 * l32 + r31;
            float m32 = r02 * l30 + r12 * l31 + r22 * l32 + r32;

            int nOff = i * 9;
            float ln00 = normals[nOff],     ln01 = normals[nOff + 1], ln02 = normals[nOff + 2];
            float ln10 = normals[nOff + 3], ln11 = normals[nOff + 4], ln12 = normals[nOff + 5];
            float ln20 = normals[nOff + 6], ln21 = normals[nOff + 7], ln22 = normals[nOff + 8];

            float n00 = rn00 * ln00 + rn10 * ln01 + rn20 * ln02;
            float n01 = rn01 * ln00 + rn11 * ln01 + rn21 * ln02;
            float n02 = rn02 * ln00 + rn12 * ln01 + rn22 * ln02;

            float n10 = rn00 * ln10 + rn10 * ln11 + rn20 * ln12;
            float n11 = rn01 * ln10 + rn11 * ln11 + rn21 * ln12;
            float n12 = rn02 * ln10 + rn12 * ln11 + rn22 * ln12;

            float n20 = rn00 * ln20 + rn10 * ln21 + rn20 * ln22;
            float n21 = rn01 * ln20 + rn11 * ln21 + rn21 * ln22;
            float n22 = rn02 * ln20 + rn12 * ln21 + rn22 * ln22;

            int currentPackedLight = isPreview || bone.glow ? FULL_BRIGHT_LIGHT : packedLight;

            for (BuiltModel.BakedCube cube : bone.cubes) {
                for (BuiltModel.BakedQuad quad : cube.quads) {
                    float qnx = quad.normalX, qny = quad.normalY, qnz = quad.normalZ;
                    float nx = n00 * qnx + n10 * qny + n20 * qnz;
                    float ny = n01 * qnx + n11 * qny + n21 * qnz;
                    float nz = n02 * qnx + n12 * qny + n22 * qnz;
                    float lenSq = nx * nx + ny * ny + nz * nz;
                    if (lenSq > 0.0f) {
                        float invLen = org.joml.Math.invsqrt(lenSq);
                        nx *= invLen;
                        ny *= invLen;
                        nz *= invLen;
                    }
                    float[] uvs = quad.packedUvs;
                    if (useJavaVector && affineRoot) {
                        JdkVectorModelMath.transformQuadPositions(
                                quad,
                                m00, m10, m20, m30,
                                m01, m11, m21, m31,
                                m02, m12, m22, m32,
                                vectorX, vectorY, vectorZ);
                        for (int v = 0; v < 4; v++) {
                            int uvOff = v << 1;
                            vertexConsumer.addVertex(vectorX[v], vectorY[v], vectorZ[v], packedColor, uvs[uvOff], uvs[uvOff + 1], packedOverlay, currentPackedLight, nx, ny, nz);
                        }
                    } else {
                        float[] pos = quad.packedPositions;
                        for (int v = 0; v < 4; v++) {
                            int pOff = v * 3;
                            int uvOff = v << 1;
                            float qx = pos[pOff];
                            float qy = pos[pOff + 1];
                            float qz = pos[pOff + 2];
                            float vx = m00 * qx + m10 * qy + m20 * qz + m30;
                            float vy = m01 * qx + m11 * qy + m21 * qz + m31;
                            float vz = m02 * qx + m12 * qy + m22 * qz + m32;
                            vertexConsumer.addVertex(vx, vy, vz, packedColor, uvs[uvOff], uvs[uvOff + 1], packedOverlay, currentPackedLight, nx, ny, nz);
                        }
                    }
                }
            }
        }
    }

    public static boolean nativeRenderModel(
            VertexConsumer vertexConsumer, PoseStack.Pose pose, Matrix4f projectionModelViewMatrix,
            boolean isCompatMode, BuiltModel mesh, float[] boneParams, float[] stateBuffer,
            int textureIndex, int renderPartMask, int packedLight, int packedOverlay,
            float r, float g, float b, float a, boolean isPreview) {

        if (mesh == null || mesh.bakedBones == null || mesh.bakedBones.isEmpty()) {
            logNativeCacheFailOnce();
            return false;
        }
        // Route NATIVE_SIMD draws through zero-allocation host affine/vector renderer by default so
        // users with legacy SAFE/AGGRESSIVE configs avoid spm-core NativeRigidRenderer's ~39x slower staging.
        if (!FORCE_CORE_NATIVE_RIGID) {
            if (LOGGED_NATIVE_RIGID_BYPASS.compareAndSet(false, true)) {
                YesSteveModel.LOGGER.info("[SM-SIMD] NATIVE_SIMD policy routed through zero-allocation host affine/vector path (set -Dspm.force.core.native.rigid=true to force spm-core NativeRigidRenderer staging)");
            }
            renderModel(
                    vertexConsumer, pose, projectionModelViewMatrix, isCompatMode,
                    mesh, boneParams, stateBuffer, textureIndex, renderPartMask,
                    packedLight, packedOverlay, r, g, b, a, isPreview, BoneRenderPass.ALL);
            return true;
        }

        BuiltModel.ComputeCache cache = mesh.ensureComputeCache();
        if (cache == null) {
            logNativeCacheFailOnce();
            return false;
        }
        int[] renderOrder = mesh.getPartMaskBoneRenderOrder(renderPartMask);
        if (renderOrder == null || renderOrder.length == 0) {
            renderOrder = FALLBACK_SCRATCH.get().fallbackRenderOrder(mesh.bakedBones.size());
        }
        BuiltModel.PartBatch partBatch = cache.batchForMask(mesh.bakedBones, renderOrder, renderPartMask);
        if (partBatch == null || partBatch.vertices().vertexCount() == 0) {
            return true;
        }

        try {
            BonePose bonePose = com.micaftic.morpher.core.gpu.SpmCoreBoneMatrixWriter.toBonePose(mesh.bakedBones, boneParams);
            CpuComputeProvider provider = AccelerationCapability.computeProvider();
            BoneMatrices matrices = provider.evaluateBones(cache.skeleton, bonePose);

            com.micaftic.morpher.core.gpu.SpmCoreBoneMatrixWriter.updateLocatorStateBuffer(
                    mesh.bakedBones, boneParams, matrices, stateBuffer);

            VertexBatch transformed = provider.renderVertices(partBatch.vertices(), matrices);
            int count = transformed.vertexCount();
            float[] pos = transformed.positions();
            float[] norm = transformed.normals();
            int[] origIds = transformed.colorsRgba();
            int[] glow = transformed.glow();
            float[] uvs = partBatch.uvs();

            Matrix4f rootPose = pose.pose();
            Matrix3f rootNormal = pose.normal();
            Vector4f tmpPos = FALLBACK_SCRATCH.get().tempPos;
            Vector3f tmpNorm = FALLBACK_SCRATCH.get().tempNorm;
            int packedColor = packArgb(a, r, g, b);

            for (int i = 0; i < count; i++) {
                int vIdx = origIds[i];
                int p = i * 3;
                tmpPos.set(pos[p], pos[p + 1], pos[p + 2], 1.0f).mul(rootPose);
                tmpNorm.set(norm[p], norm[p + 1], norm[p + 2]).mul(rootNormal).normalize();
                int light = (isPreview || glow[i] != 255) ? FULL_BRIGHT_LIGHT : packedLight;
                vertexConsumer.addVertex(
                        tmpPos.x(), tmpPos.y(), tmpPos.z(),
                        packedColor,
                        uvs[vIdx * 2], uvs[vIdx * 2 + 1],
                        packedOverlay, light,
                        tmpNorm.x(), tmpNorm.y(), tmpNorm.z()
                );
            }
            return true;
        } catch (Throwable t) {
            YesSteveModel.LOGGER.error("spm-core SIMD vertex computation failed; using Java fallback", t);
            return false;
        }
    }

    private static int packArgb(float a, float r, float g, float b) {
        return ((int) (a * 255) << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private static void logPreviewSimdFallbackOnce() {
        if (SIMD_PREVIEW_FALLBACK_LOGGED.compareAndSet(false, true)) {
            YesSteveModel.LOGGER.info("[SM] Preview/overlay SIMD render failed, falling back to Java renderModel");
        }
    }

    private static void logNativeCacheFailOnce() {
        if (NATIVE_CACHE_FAIL_LOGGED.compareAndSet(false, true)) {
            YesSteveModel.LOGGER.info("[SM] nativeRenderModel: compute cache unavailable (empty bakedBones)");
        }
    }

    private static final class RenderScratch {
        private final Vector4f tempPos = new Vector4f();
        private final Vector3f tempNorm = new Vector3f();
        private final float[] vectorX = new float[4];
        private final float[] vectorY = new float[4];
        private final float[] vectorZ = new float[4];
        private int[] allBoneRenderOrder = new int[0];

        private int[] fallbackRenderOrder(int boneCount) {
            if (allBoneRenderOrder.length != boneCount) {
                allBoneRenderOrder = new int[boneCount];
                for (int i = 0; i < boneCount; i++) {
                    allBoneRenderOrder[i] = i;
                }
            }
            return allBoneRenderOrder;
        }
    }
}
