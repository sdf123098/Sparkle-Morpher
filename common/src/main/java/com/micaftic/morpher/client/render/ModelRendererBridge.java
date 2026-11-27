

package com.micaftic.morpher.client.render;

import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.SubmitRenderContext;
import com.micaftic.morpher.core.config.ConfigPolicies;

import com.micaftic.morpher.util.log.ChatLogger;
import dev.spm.core.acceleration.CpuComputeProvider;
import dev.spm.core.api.BoneMatrices;
import dev.spm.core.api.BonePose;
import dev.spm.core.api.VertexBatch;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import com.micaftic.morpher.core.compat.oculus.OculusCompat;
import com.micaftic.morpher.core.compat.optifine.OptiFineDetector;
import com.micaftic.morpher.core.gpu.Blaze3DRenderPath;
import com.micaftic.morpher.core.gpu.GpuCapability;
import com.micaftic.morpher.core.gpu.GpuDebugLog;
import com.micaftic.morpher.core.gpu.GpuRenderPath;
import com.micaftic.morpher.core.acceleration.AccelerationCapability;
import com.micaftic.morpher.core.render.RenderBackend;
import com.micaftic.morpher.core.render.RenderBackendDecision;
import com.micaftic.morpher.core.render.NativeSimdValidator;
import com.micaftic.morpher.core.vector.JdkVectorModelMath;
import com.micaftic.morpher.core.vector.VectorApiCapability;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

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

    public static void renderMesh(VertexConsumer buffer, PoseStack.Pose pose, BuiltModel model, float[] boneParams, float[] stateBuffer, int textureIndex, int renderPartMask, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        renderMesh(buffer, pose, model, boneParams, stateBuffer, textureIndex, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha, null);
    }

    public static void renderMesh(VertexConsumer buffer, PoseStack.Pose pose, BuiltModel model, float[] boneParams, float[] stateBuffer, int textureIndex, int renderPartMask, int packedLight, int packedOverlay, float red, float green, float blue, float alpha, net.minecraft.resources.Identifier textureLocation) {
        renderMesh(buffer, pose, model, boneParams, stateBuffer, textureIndex, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha, textureLocation, true);
    }

    public static void renderMesh(VertexConsumer buffer, PoseStack.Pose pose, BuiltModel model, float[] boneParams, float[] stateBuffer, int textureIndex, int renderPartMask, int packedLight, int packedOverlay, float red, float green, float blue, float alpha, net.minecraft.resources.Identifier textureLocation, boolean allowDirectGpuRenderer) {
        renderMeshPass(buffer, pose, model, boneParams, stateBuffer, textureIndex, renderPartMask,
                packedLight, packedOverlay, red, green, blue, alpha, textureLocation,
                allowDirectGpuRenderer, BoneRenderPass.ALL);
    }

    public static void renderMeshPass(VertexConsumer buffer, PoseStack.Pose pose, BuiltModel model,
            float[] boneParams, float[] stateBuffer, int textureIndex, int renderPartMask,
            int packedLight, int packedOverlay, float red, float green, float blue, float alpha,
            net.minecraft.resources.Identifier textureLocation, boolean allowDirectGpuRenderer,
            BoneRenderPass boneRenderPass) {
        OculusCompat.updatePBRState();
        projectionModelViewMatrix.identity();
        boolean isPreview = ModelPreviewRenderer.isPreview() || ModelPreviewRenderer.isExtraPlayer();
        boolean shaderPackInUse = OculusCompat.isShaderPackInUse() && !isPreview;
        boolean disableGlow = shouldDisableModelGlow(shaderPackInUse);

        // Submit-based world renders must keep the normal geometry path so the
        // entity still reaches the feature/shadow pipeline.
        boolean translucentTexture = model.isTranslucentTexture(textureIndex);
        com.micaftic.morpher.core.render.NativeSimdPolicy nativePolicy = ConfigPolicies.graphics().nativeSimdPolicy();
        // Shader packs own the active entity program and uniforms (including entityId).
        // Direct VAO draws can reuse stale per-entity state, so keep them on the
        // VertexConsumer-backed SIMD/Java path while a pack is active.
        boolean allowDirectGpu = allowDirectGpuRenderer && !shaderPackInUse;
        RenderBackendDecision backend = RenderBackendDecision.choose(model, allowDirectGpu, translucentTexture, disableGlow, textureLocation, nativePolicy);
        GpuDebugLog.verbose("entry texture={} allowGpu={} backend={} reason={} translucent={} disableGlow={} shaderPack={} preview={} firstPerson={} submitContext={} worldRender={} compat={} gpuCfg={} nativeSimdPolicy={} validationMode={} nativeLoaded={} nativeReason={} negFaces={} optStats={} consvOnly={}",
                textureLocation, allowDirectGpu, backend.backend, backend.reason, translucentTexture, disableGlow, shaderPackInUse,
                ModelPreviewRenderer.isPreview() || ModelPreviewRenderer.isExtraPlayer(), ModelPreviewRenderer.isFirstPerson(), SubmitRenderContext.get() != null, ModelPreviewRenderer.isWorldRender(),
                ConfigPolicies.render().useCompatibilityRenderer(), ConfigPolicies.render().useGpuRenderer(), nativePolicy,
                ConfigPolicies.graphics().nativeSimdValidationMode(),
                AccelerationCapability.isLoaded(), AccelerationCapability.getReason(),
                model.optimizationStats == null ? -1 : model.optimizationStats.negativeSizedFaces,
                model.optimizationStats != null,
	                model.conservativeRenderOnly);
        // R10.1：后端通过 RenderBackend 接口隔离（决策 RenderBackendDecision + 实现 RenderBackends）
        RenderBackend.RenderRequest request = new RenderBackend.RenderRequest(
                buffer, pose, projectionModelViewMatrix, model, boneParams, stateBuffer,
                textureIndex, renderPartMask, packedLight, packedOverlay,
                red, green, blue, alpha, textureLocation, translucentTexture, disableGlow,
                isPreview, OptiFineDetector.isOptifinePresent(), boneRenderPass);
        if (boneRenderPass == BoneRenderPass.ALL && backend.backend == RenderBackendDecision.Backend.BLAZE3D_GPU) {
            if (RenderBackends.get(RenderBackendDecision.Backend.BLAZE3D_GPU).tryRender(request)) {
                return;
            }
            GpuDebugLog.verbose("entry Blaze3DRenderPath fallback texture={}", textureLocation);
            backend = RenderBackendDecision.choose(model, false, translucentTexture, disableGlow, textureLocation, nativePolicy);
        }
        if (boneRenderPass == BoneRenderPass.ALL && backend.backend == RenderBackendDecision.Backend.GPU) {
            if (RenderBackends.get(RenderBackendDecision.Backend.GPU).tryRender(request)) {
                return;
            }
            GpuDebugLog.verbose("entry GpuRenderPath fallback texture={}", textureLocation);
        }
        if (boneRenderPass == BoneRenderPass.ALL && backend.backend == RenderBackendDecision.Backend.NATIVE_SIMD) { // WIP: SIMD MODEL RENDER
            if (RenderBackends.get(RenderBackendDecision.Backend.NATIVE_SIMD).tryRender(request)) {
                return;
            }
        }
        // Java 兜底（含 GLOW/NON_GLOW pass 与非 ALL pass）
        RenderBackends.get(RenderBackendDecision.Backend.JAVA).tryRender(request);
    }

    private static boolean shouldDisableModelGlow(boolean shaderPackInUse) {
        return shaderPackInUse && ConfigPolicies.render().disableModelGlowInShaderpack();
    }

    public static boolean shouldUseEmissiveBoneMaterial(BuiltModel model) {
        if (model == null || !model.hasGlowBones()) return false;
        OculusCompat.updatePBRState();
        boolean preview = ModelPreviewRenderer.isPreview() || ModelPreviewRenderer.isExtraPlayer();
        boolean shaderPackInUse = OculusCompat.isShaderPackInUse() && !preview;
        return !shouldDisableModelGlow(shaderPackInUse);
    }

    public static void renderModel(
            VertexConsumer vertexConsumer,
            PoseStack.Pose pose,
            Matrix4f projectionModelViewMatrix,
            boolean isCompatMode,
            BuiltModel mesh,
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
            BuiltModel mesh,
            float[] boneParams,
            float[] stateBuffer,
            int textureIndex, int renderPartMask,
            int packedLight, int packedOverlay,
            float r, float g, float b, float a,
            boolean isPreview,
            boolean disableGlow) {

        renderModel(vertexConsumer, pose, projectionModelViewMatrix, isCompatMode, mesh,
                boneParams, stateBuffer, textureIndex, renderPartMask, packedLight, packedOverlay,
                r, g, b, a, isPreview, disableGlow, BoneRenderPass.ALL);
    }

    public static void renderModel(
            VertexConsumer vertexConsumer,
            PoseStack.Pose pose,
            Matrix4f projectionModelViewMatrix,
            boolean isCompatMode,
            BuiltModel mesh,
            float[] boneParams,
            float[] stateBuffer,
            int textureIndex, int renderPartMask,
            int packedLight, int packedOverlay,
            float r, float g, float b, float a,
            boolean isPreview,
            boolean disableGlow,
            BoneRenderPass boneRenderPass) {

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
        int packedColor = ((int) (a * 255) << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);

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

            int currentPackedLight = bone.glow && !disableGlow ? FULL_BRIGHT_LIGHT : packedLight;

            // Most rigid bones have an orthonormal 3x3 normal matrix; when checked once per bone,
            // unit quad normals stay unit length after transform so we skip per-quad invsqrt.
            boolean orthonormalNormals =
                    Math.abs((n00 * n00 + n01 * n01 + n02 * n02) - 1.0f) < 1.0e-3f
                    && Math.abs((n10 * n10 + n11 * n11 + n12 * n12) - 1.0f) < 1.0e-3f
                    && Math.abs((n20 * n20 + n21 * n21 + n22 * n22) - 1.0f) < 1.0e-3f
                    && Math.abs(n00 * n10 + n01 * n11 + n02 * n12) < 1.0e-3f
                    && Math.abs(n00 * n20 + n01 * n21 + n02 * n22) < 1.0e-3f
                    && Math.abs(n10 * n20 + n11 * n21 + n12 * n22) < 1.0e-3f;

            BuiltModel.BakedQuad[] flatQuads = bone.getOrBuildFlatQuads();
            int quadCount = flatQuads.length;
            if (useJavaVector && affineRoot) {
                for (int q = 0; q < quadCount; q++) {
                    BuiltModel.BakedQuad quad = flatQuads[q];
                    float qnx = quad.normalX, qny = quad.normalY, qnz = quad.normalZ;
                    float nx = n00 * qnx + n10 * qny + n20 * qnz;
                    float ny = n01 * qnx + n11 * qny + n21 * qnz;
                    float nz = n02 * qnx + n12 * qny + n22 * qnz;
                    if (!orthonormalNormals) {
                        float lenSq = nx * nx + ny * ny + nz * nz;
                        if (lenSq > 0.0f) {
                            float invLen = org.joml.Math.invsqrt(lenSq);
                            nx *= invLen;
                            ny *= invLen;
                            nz *= invLen;
                        }
                    }
                    float[] uvs = quad.packedUvs;
                    JdkVectorModelMath.transformQuadPositions(
                            quad,
                            m00, m10, m20, m30,
                            m01, m11, m21, m31,
                            m02, m12, m22, m32,
                            vectorX, vectorY, vectorZ);
                    vertexConsumer.addVertex(vectorX[0], vectorY[0], vectorZ[0], packedColor, uvs[0], uvs[1], packedOverlay, currentPackedLight, nx, ny, nz);
                    vertexConsumer.addVertex(vectorX[1], vectorY[1], vectorZ[1], packedColor, uvs[2], uvs[3], packedOverlay, currentPackedLight, nx, ny, nz);
                    vertexConsumer.addVertex(vectorX[2], vectorY[2], vectorZ[2], packedColor, uvs[4], uvs[5], packedOverlay, currentPackedLight, nx, ny, nz);
                    vertexConsumer.addVertex(vectorX[3], vectorY[3], vectorZ[3], packedColor, uvs[6], uvs[7], packedOverlay, currentPackedLight, nx, ny, nz);
                }
            } else {
                for (int q = 0; q < quadCount; q++) {
                    BuiltModel.BakedQuad quad = flatQuads[q];
                    float qnx = quad.normalX, qny = quad.normalY, qnz = quad.normalZ;
                    float nx = n00 * qnx + n10 * qny + n20 * qnz;
                    float ny = n01 * qnx + n11 * qny + n21 * qnz;
                    float nz = n02 * qnx + n12 * qny + n22 * qnz;
                    if (!orthonormalNormals) {
                        float lenSq = nx * nx + ny * ny + nz * nz;
                        if (lenSq > 0.0f) {
                            float invLen = org.joml.Math.invsqrt(lenSq);
                            nx *= invLen;
                            ny *= invLen;
                            nz *= invLen;
                        }
                    }
                    float[] uvs = quad.packedUvs;
                    float[] pos = quad.packedPositions;
                    float qx0 = pos[0], qy0 = pos[1], qz0 = pos[2];
                    vertexConsumer.addVertex(
                            m00 * qx0 + m10 * qy0 + m20 * qz0 + m30,
                            m01 * qx0 + m11 * qy0 + m21 * qz0 + m31,
                            m02 * qx0 + m12 * qy0 + m22 * qz0 + m32,
                            packedColor, uvs[0], uvs[1], packedOverlay, currentPackedLight, nx, ny, nz);
                    float qx1 = pos[3], qy1 = pos[4], qz1 = pos[5];
                    vertexConsumer.addVertex(
                            m00 * qx1 + m10 * qy1 + m20 * qz1 + m30,
                            m01 * qx1 + m11 * qy1 + m21 * qz1 + m31,
                            m02 * qx1 + m12 * qy1 + m22 * qz1 + m32,
                            packedColor, uvs[2], uvs[3], packedOverlay, currentPackedLight, nx, ny, nz);
                    float qx2 = pos[6], qy2 = pos[7], qz2 = pos[8];
                    vertexConsumer.addVertex(
                            m00 * qx2 + m10 * qy2 + m20 * qz2 + m30,
                            m01 * qx2 + m11 * qy2 + m21 * qz2 + m31,
                            m02 * qx2 + m12 * qy2 + m22 * qz2 + m32,
                            packedColor, uvs[4], uvs[5], packedOverlay, currentPackedLight, nx, ny, nz);
                    float qx3 = pos[9], qy3 = pos[10], qz3 = pos[11];
                    vertexConsumer.addVertex(
                            m00 * qx3 + m10 * qy3 + m20 * qz3 + m30,
                            m01 * qx3 + m11 * qy3 + m21 * qz3 + m31,
                            m02 * qx3 + m12 * qy3 + m22 * qz3 + m32,
                            packedColor, uvs[6], uvs[7], packedOverlay, currentPackedLight, nx, ny, nz);
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
            GpuDebugLog.verbose("nativeRenderModel: compute cache unavailable (empty bakedBones)");
            return false;
        }
        // Benchmarks on spm-core 1.0.0 show NativeRigidRenderer's per-call NativeArena + immutable
        // array cloning takes ~5.85ms vs ~0.15ms (~39x slower) on 500-bone/36k-vertex models.
        // Route NATIVE_SIMD draws through our zero-allocation affine/vector renderer by default so
        // users with legacy SAFE/AGGRESSIVE configs get full performance without losing compatibility.
        if (!FORCE_CORE_NATIVE_RIGID) {
            if (LOGGED_NATIVE_RIGID_BYPASS.compareAndSet(false, true)) {
                GpuDebugLog.info("NATIVE_SIMD policy routed through zero-allocation host affine/vector path (set -Dspm.force.core.native.rigid=true to force spm-core NativeRigidRenderer staging)");
            }
            renderModel(
                    vertexConsumer, pose, projectionModelViewMatrix, isCompatMode,
                    mesh, boneParams, stateBuffer, textureIndex, renderPartMask,
                    packedLight, packedOverlay, r, g, b, a, isPreview, false, BoneRenderPass.ALL);
            return true;
        }

        BuiltModel.ComputeCache cache = mesh.ensureComputeCache();
        if (cache == null) {
            GpuDebugLog.verbose("nativeRenderModel: compute cache unavailable");
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
            int packedColor = ((int) (a * 255) << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);

            for (int i = 0; i < count; i++) {
                int vIdx = origIds[i];
                int p = i * 3;
                tmpPos.set(pos[p], pos[p + 1], pos[p + 2], 1.0f).mul(rootPose);
                tmpNorm.set(norm[p], norm[p + 1], norm[p + 2]).mul(rootNormal).normalize();
                int light = glow[i] != 255 ? FULL_BRIGHT_LIGHT : packedLight;
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
            GpuDebugLog.error("spm-core SIMD vertex computation failed; using Java fallback", t);
            return false;
        }
    }

    private static final class RenderScratch {
        final Vector4f tempPos = new Vector4f();
        final Vector3f tempNorm = new Vector3f();
        final float[] vectorX = new float[4];
        final float[] vectorY = new float[4];
        final float[] vectorZ = new float[4];
        int[] allBoneRenderOrder = new int[0];

        int[] fallbackRenderOrder(int boneCount) {
            if (allBoneRenderOrder.length < boneCount) {
                allBoneRenderOrder = new int[boneCount];
                for (int i = 0; i < boneCount; i++) {
                    allBoneRenderOrder[i] = i;
                }
            }
            return allBoneRenderOrder;
        }
    }
}
