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
            if (boneRenderPass == BoneRenderPass.ALL
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
        if (boneRenderPass == BoneRenderPass.ALL
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

            Matrix4f localBoneMat = boneLocalTransforms[i];
            globalBoneMat.set(rootPoseMat).mul(localBoneMat);
            localBoneMat.normal(localNormalMat);
            globalNormalMat.set(rootNormalMC).mul(localNormalMat);

            int currentPackedLight = isPreview || bone.glow ? FULL_BRIGHT_LIGHT : packedLight;

            for (BuiltModel.BakedCube cube : bone.cubes) {
                for (BuiltModel.BakedQuad quad : cube.quads) {
                    tempNorm.set(quad.normalX, quad.normalY, quad.normalZ).mul(globalNormalMat).normalize();
                    if (useJavaVector) {
                        JdkVectorModelMath.transformQuadPositions(quad, globalBoneMat, scratch.vectorX, scratch.vectorY, scratch.vectorZ);
                        for (int v = 0; v < 4; v++) {
                            vertexConsumer.addVertex(scratch.vectorX[v], scratch.vectorY[v], scratch.vectorZ[v], packedColor, quad.u(v), quad.v(v), packedOverlay, currentPackedLight, tempNorm.x(), tempNorm.y(), tempNorm.z());
                        }
                    } else {
                        for (int v = 0; v < 4; v++) {
                            tempPos.set(quad.x(v), quad.y(v), quad.z(v), 1.0f).mul(globalBoneMat);
                            vertexConsumer.addVertex(tempPos.x(), tempPos.y(), tempPos.z(), packedColor, quad.u(v), quad.v(v), packedOverlay, currentPackedLight, tempNorm.x(), tempNorm.y(), tempNorm.z());
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
            return count > 0;
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
        private final Matrix4f globalBoneMat = new Matrix4f();
        private final Matrix3f localNormalMat = new Matrix3f();
        private final Matrix3f globalNormalMat = new Matrix3f();
        private final Vector4f tempPos = new Vector4f();
        private final Vector3f tempNorm = new Vector3f();
        private final float[] vectorX = new float[4];
        private final float[] vectorY = new float[4];
        private final float[] vectorZ = new float[4];
        private Matrix4f[] boneLocalTransforms = new Matrix4f[0];
        private boolean[] boneVisible = new boolean[0];
        private int[] allBoneRenderOrder = new int[0];

        private void ensureBoneCapacity(int size) {
            if (boneLocalTransforms.length >= size) {
                return;
            }
            int newSize = Math.max(size, boneLocalTransforms.length == 0 ? 16 : boneLocalTransforms.length * 2);
            boneLocalTransforms = Arrays.copyOf(boneLocalTransforms, newSize);
            boneVisible = Arrays.copyOf(boneVisible, newSize);
        }

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
