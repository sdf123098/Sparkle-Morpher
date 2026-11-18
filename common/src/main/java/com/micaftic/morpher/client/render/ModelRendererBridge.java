

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
import com.micaftic.morpher.core.gpu.GpuCapability;
import com.micaftic.morpher.core.gpu.GpuDebugLog;
import com.micaftic.morpher.core.gpu.GpuRenderPath;
import com.micaftic.morpher.core.acceleration.AccelerationCapability;
import com.micaftic.morpher.core.render.RenderBackend;
import com.micaftic.morpher.core.render.RenderBackendDecision;
import com.micaftic.morpher.core.render.NativeSimdValidator;
import com.micaftic.morpher.core.vector.JdkVectorModelMath;
import com.micaftic.morpher.core.vector.VectorApiCapability;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

public class ModelRendererBridge {
    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;

    public enum BoneRenderPass {
        ALL,
        NON_GLOW,
        GLOW
    }

    private static final Matrix4f projectionModelViewMatrix = new Matrix4f();
    private static final ThreadLocal<int[]> NATIVE_SUBMITTED_VERTICES = ThreadLocal.withInitial(() -> new int[1]);
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
        if (model == null || model.bakedBones == null) return false;
        boolean hasGlow = false;
        for (BuiltModel.BakedBone bone : model.bakedBones) {
            if (bone.glow) {
                hasGlow = true;
                break;
            }
        }
        if (!hasGlow) return false;
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

        // TODO: reduce GC pressure.
        RenderScratch scratch = FALLBACK_SCRATCH.get();
        scratch.ensureBoneCapacity(boneCount);

        Matrix4f rootPoseMat = pose.pose();
        Matrix3f rootNormalMC = pose.normal();
        Matrix4f globalBoneMat = scratch.globalBoneMat;
        Matrix3f localNormalMat = scratch.localNormalMat;
        Matrix3f globalNormalMat = scratch.globalNormalMat;
        Vector4f tempPos = scratch.tempPos;
        Vector3f tempNorm = scratch.tempNorm;
        Matrix4f[] boneLocalTransforms = scratch.boneLocalTransforms;
        boolean[] boneVisible = scratch.boneVisible;
        boolean javaVectorRequested = ConfigPolicies.graphics().experimentalJavaVectorRenderer();
        VectorApiCapability.warnIfRequested(javaVectorRequested);
        boolean useJavaVector = javaVectorRequested && VectorApiCapability.isAvailable();
        if (!com.micaftic.morpher.core.gpu.SpmCoreBoneMatrixWriter.evaluateBoneLocalTransforms(
                mesh, boneParams, stateBuffer, boneLocalTransforms, boneVisible)) {
            return;
        }

        int[] renderBoneOrder = mesh.getPartMaskBoneRenderOrder(renderPartMask);
        if (renderBoneOrder == null || renderBoneOrder.length == 0) {
            renderBoneOrder = scratch.fallbackRenderOrder(boneCount);
        }

        for (int orderIndex = 0; orderIndex < renderBoneOrder.length; orderIndex++) {
            int i = renderBoneOrder[orderIndex];
            if (i < 0 || i >= boneCount) {
                continue;
            }
            if (!boneVisible[i]) {
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

            Matrix4f localBoneMat = boneLocalTransforms[i];
            globalBoneMat.set(rootPoseMat).mul(localBoneMat);

            // Compute global normal matrix.
            localBoneMat.normal(localNormalMat);
            globalNormalMat.set(rootNormalMC).mul(localNormalMat);

            int currentPackedLight = bone.glow && !disableGlow ? FULL_BRIGHT_LIGHT : packedLight;

            for (BuiltModel.BakedCube cube : bone.cubes) {
                for (BuiltModel.BakedQuad quad : cube.quads) {
                    tempNorm.set(quad.normalX, quad.normalY, quad.normalZ).mul(globalNormalMat).normalize();
                    if (useJavaVector) {
                        JdkVectorModelMath.transformQuadPositions(quad, globalBoneMat, scratch.vectorX, scratch.vectorY, scratch.vectorZ);
                        for (int v = 0; v < 4; v++) {
                            vertexConsumer.addVertex(scratch.vectorX[v], scratch.vectorY[v], scratch.vectorZ[v], ((int)(a * 255) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255), quad.u(v), quad.v(v), packedOverlay, currentPackedLight, tempNorm.x(), tempNorm.y(), tempNorm.z());
                        }
                    } else {
                        for (int v = 0; v < 4; v++) {
                            tempPos.set(quad.x(v), quad.y(v), quad.z(v), 1.0f).mul(globalBoneMat);
                            vertexConsumer.addVertex(tempPos.x(), tempPos.y(), tempPos.z(), ((int)(a * 255) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255), quad.u(v), quad.v(v), packedOverlay, currentPackedLight, tempNorm.x(), tempNorm.y(), tempNorm.z());
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

        BuiltModel.ComputeCache cache = mesh.ensureComputeCache();
        if (cache == null || mesh.bakedBones == null || mesh.bakedBones.isEmpty()) {
            GpuDebugLog.verbose("nativeRenderModel: compute cache unavailable (empty bakedBones)");
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
                int light = (isPreview || glow[i] != 255) ? FULL_BRIGHT_LIGHT : packedLight;
                vertexConsumer.addVertex(
                        tmpPos.x(), tmpPos.y(), tmpPos.z(),
                        packedColor,
                        uvs[vIdx * 2], uvs[vIdx * 2 + 1],
                        packedOverlay, light,
                        tmpNorm.x(), tmpNorm.y(), tmpNorm.z()
                );
            }
            return count > 0;
        } catch (Throwable t) {
            GpuDebugLog.error("spm-core SIMD vertex computation failed; using Java fallback", t);
            return false;
        }
    }

    private static final class RenderScratch {
        final Matrix4f globalBoneMat = new Matrix4f();
        final Matrix3f localNormalMat = new Matrix3f();
        final Matrix3f globalNormalMat = new Matrix3f();
        final Vector4f tempPos = new Vector4f();
        final Vector3f tempNorm = new Vector3f();
        final float[] vectorX = new float[4];
        final float[] vectorY = new float[4];
        final float[] vectorZ = new float[4];
        Matrix4f[] boneLocalTransforms = new Matrix4f[0];
        boolean[] boneVisible = new boolean[0];
        int[] allBoneRenderOrder = new int[0];

        void ensureBoneCapacity(int boneCount) {
            if (boneLocalTransforms.length < boneCount) {
                boneLocalTransforms = Arrays.copyOf(boneLocalTransforms, boneCount);
                boneVisible = Arrays.copyOf(boneVisible, boneCount);
            }
        }

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
