

package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.config.GeneralConfig;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.util.log.ChatLogger;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import com.micaftic.morpher.core.compat.oculus.OculusCompat;
import com.micaftic.morpher.core.compat.optifine.OptiFineDetector;
import com.micaftic.morpher.core.gpu.GpuCapability;
import com.micaftic.morpher.core.gpu.GpuRenderPath;
import com.micaftic.morpher.core.gpu.IrisRenderPath;
import com.micaftic.morpher.core.acceleration.AccelerationCapability;
import com.micaftic.morpher.core.vector.JdkVectorModelMath;
import com.micaftic.morpher.core.vector.VectorApiCapability;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

public class ModelRendererBridge {
    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;

    private static final Matrix4f projectionModelViewMatrix = new Matrix4f();
    private static final ThreadLocal<RenderScratch> FALLBACK_SCRATCH = ThreadLocal.withInitial(RenderScratch::new);

    public static void renderMesh(VertexConsumer buffer, PoseStack.Pose pose, GeoModel model, float[] boneParams, float[] stateBuffer, int textureIndex, int renderPartMask, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        renderMesh(buffer, pose, model, boneParams, stateBuffer, textureIndex, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha, null);
    }

    public static void renderMesh(VertexConsumer buffer, PoseStack.Pose pose, GeoModel model, float[] boneParams, float[] stateBuffer, int textureIndex, int renderPartMask, int packedLight, int packedOverlay, float red, float green, float blue, float alpha, net.minecraft.resources.ResourceLocation textureLocation) {
        OculusCompat.updatePBRState();
        RenderSystem.getProjectionMatrix().mul(RenderSystem.getModelViewMatrix(), projectionModelViewMatrix);
        boolean isPreview = ModelPreviewRenderer.isPreview() || ModelPreviewRenderer.isExtraPlayer();

        if (isPreview) {
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
                    FULL_BRIGHT_LIGHT,
                    packedOverlay,
                    red, green, blue, alpha,
                    true
            );
            return;
        }

        boolean useGpuRenderer = textureLocation != null && AccelerationCapability.canBuildGpuMesh() && !GeneralConfig.USE_COMPATIBILITY_RENDERER.get() && GeneralConfig.USE_GPU_RENDERER.get();

        if (useGpuRenderer) {

            if(!GpuCapability.isAvailable())
            {
                ChatLogger.INSTANCE.logFormatted("Disabled GPU renderer for: " + GpuCapability.getReason());
                GeneralConfig.USE_GPU_RENDERER.set(false);
                return;
            }

            if (OculusCompat.isShaderPackInUse() && !isPreview) {
                if (IrisRenderPath.tryRender(model, pose, boneParams, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha, textureLocation)) {
                    return;
                }
            } else {
                if (GpuRenderPath.tryRender(model, pose, boneParams, stateBuffer, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha, textureLocation, model.isTranslucentTexture(textureIndex))) {
                    return;
                }
            }
        }

        if (AccelerationCapability.canRenderSimd() && !GeneralConfig.USE_COMPATIBILITY_RENDERER.get()) { // WIP: SIMD MODEL RENDER
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
                    isPreview
            );
        }
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

        if (mesh.bakedBones == null || mesh.bakedBones.isEmpty()) return;
        int boneCount = mesh.bakedBones.size();
        RenderScratch scratch = FALLBACK_SCRATCH.get();
        scratch.ensureBoneCapacity(boneCount);

        // TODO: 修復GC壓力
        Matrix4f rootPoseMat = pose.pose();
        Matrix3f rootNormalMC = pose.normal();
        Matrix4f projMat = RenderSystem.getProjectionMatrix();

        Matrix4f identityMat = scratch.identityMat.identity();
        Matrix4f globalBoneMat = scratch.globalBoneMat;
        Matrix4f projBoneMat = scratch.projBoneMat;
        Matrix3f localNormalMat = scratch.localNormalMat;
        Matrix3f globalNormalMat = scratch.globalNormalMat;

        Vector4f p1 = scratch.p1;
        Vector4f p2 = scratch.p2;
        Vector4f p3 = scratch.p3;
        Vector4f tempPos = scratch.tempPos;
        Vector3f tempNorm = scratch.tempNorm;
        Matrix4f[] boneLocalTransforms = scratch.boneLocalTransforms;
        boolean[] boneVisible = scratch.boneVisible;
        boolean[] boneComputed = scratch.boneComputed;
        boolean useJavaVector = GeneralConfig.safeGet(GeneralConfig.EXPERIMENTAL_JAVA_VECTOR_RENDERER, false) && VectorApiCapability.isAvailable();
        int[] boneOrder = mesh.bakedBoneOrder;
        if (boneOrder != null && boneOrder.length == boneCount) {
            for (int orderIndex = 0; orderIndex < boneCount; orderIndex++) {
                calculateBoneMatrixLinear(boneOrder[orderIndex], mesh.bakedBones, boneParams, boneLocalTransforms, boneVisible, identityMat, stateBuffer);
            }
        } else {
            Arrays.fill(boneComputed, 0, boneCount, false);
            for (int i = 0; i < boneCount; i++) {
                calculateBoneMatrix(i, mesh.bakedBones, boneParams, boneLocalTransforms, boneVisible, boneComputed, identityMat, stateBuffer);
            }
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

            GeoModel.BakedBone bone = mesh.bakedBones.get(i);
            if (renderPartMask != 0 && bone.partMask != renderPartMask && bone.partMask != 3) {
                continue;
            }

            Matrix4f localBoneMat = boneLocalTransforms[i];
            globalBoneMat.set(rootPoseMat).mul(localBoneMat);
            projBoneMat.set(projMat).mul(globalBoneMat);

            // GUI previews keep the -Z mirror in the local PoseStack. Use the
            // screen-space culler there to avoid drawing cutout back faces while
            // preserving the old determinant guard for world rendering.
            boolean cullFaces = isPreview || globalBoneMat.determinant3x3() >= 0.0f;

            // 法線全域矩陣
            localBoneMat.normal(localNormalMat);
            globalNormalMat.set(rootNormalMC).mul(localNormalMat);

            int currentPackedLight = isPreview || bone.glow ? FULL_BRIGHT_LIGHT : packedLight;

            for (GeoModel.BakedCube cube : bone.cubes) {
                for (GeoModel.BakedQuad quad : cube.quads) {
                    if (cube.cullable && cullFaces) {
                        p1.set(quad.x(0), quad.y(0), quad.z(0), 1.0f).mul(projBoneMat);
                        p2.set(quad.x(1), quad.y(1), quad.z(1), 1.0f).mul(projBoneMat);
                        p3.set(quad.x(2), quad.y(2), quad.z(2), 1.0f).mul(projBoneMat);
                        float det = p1.x() * (p2.y() * p3.w() - p3.y() * p2.w()) - p2.x() * (p1.y() * p3.w() - p3.y() * p1.w()) + p3.x() * (p1.y() * p2.w() - p2.y() * p1.w());
                        if (det <= 0.0f) {
                            continue;
                        }
                    }
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

    private static void calculateBoneMatrixLinear(int idx, java.util.List<GeoModel.BakedBone> bones, float[] boneParams, Matrix4f[] cache, boolean[] visibleCache, Matrix4f rootPose, float[] stateBuffer) {
        GeoModel.BakedBone bone = bones.get(idx);
        Matrix4f parentMatrix = rootPose;
        boolean isVisible = true;

        if (bone.parentIdx != -1) {
            parentMatrix = cache[bone.parentIdx];
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
            int offset = idx * 4;
            if (offset + 2 < stateBuffer.length) {
                stateBuffer[offset + 0] = -localMat.m30() * 16;
                stateBuffer[offset + 1] = localMat.m31() * 16;
                stateBuffer[offset + 2] = localMat.m32() * 16;
            }
        }

        localMat.translate(-bone.pivotX / 16f, -bone.pivotY / 16f, -bone.pivotZ / 16f);
        visibleCache[idx] = isVisible;
    }

    private static Matrix4f calculateBoneMatrix(int idx, java.util.List<GeoModel.BakedBone> bones, float[] boneParams, Matrix4f[] cache, boolean[] visibleCache, boolean[] computedCache, Matrix4f rootPose, float[] stateBuffer) {
        if (computedCache[idx]) return cache[idx];

        GeoModel.BakedBone bone = bones.get(idx);
        Matrix4f parentMatrix = rootPose;
        boolean isVisible = true;

        if (bone.parentIdx != -1) {
            parentMatrix = calculateBoneMatrix(bone.parentIdx, bones, boneParams, cache, visibleCache, computedCache, rootPose, stateBuffer);
            // 如果父骨骼不可見，子骨骼必然跟著不可見
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
        visibleCache[idx] = isVisible; // 保存當前骨骼的可見性
        computedCache[idx] = true;
        return localMat;
    }

    private static final float[] matrixTransferArray = new float[48];
    @SuppressWarnings("unused") // TODO: native中直接往VertexConsumer中的buffer写入顶点
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

        Matrix4f projMat = RenderSystem.getProjectionMatrix();

        pose.pose().get(matrixTransferArray, 0);
        pose.normal().get(matrixTransferArray, 16);
        projMat.get(matrixTransferArray, 32);

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
        private final Matrix4f identityMat = new Matrix4f();
        private final Matrix4f globalBoneMat = new Matrix4f();
        private final Matrix4f projBoneMat = new Matrix4f();
        private final Matrix3f localNormalMat = new Matrix3f();
        private final Matrix3f globalNormalMat = new Matrix3f();
        private final Vector4f p1 = new Vector4f();
        private final Vector4f p2 = new Vector4f();
        private final Vector4f p3 = new Vector4f();
        private final Vector4f tempPos = new Vector4f();
        private final Vector3f tempNorm = new Vector3f();
        private final float[] vectorX = new float[4];
        private final float[] vectorY = new float[4];
        private final float[] vectorZ = new float[4];
        private Matrix4f[] boneLocalTransforms = new Matrix4f[0];
        private boolean[] boneVisible = new boolean[0];
        private boolean[] boneComputed = new boolean[0];
        private int[] allBoneRenderOrder = new int[0];

        private void ensureBoneCapacity(int size) {
            if (boneLocalTransforms.length >= size) {
                return;
            }
            int newSize = Math.max(size, boneLocalTransforms.length == 0 ? 16 : boneLocalTransforms.length * 2);
            boneLocalTransforms = Arrays.copyOf(boneLocalTransforms, newSize);
            boneVisible = Arrays.copyOf(boneVisible, newSize);
            boneComputed = Arrays.copyOf(boneComputed, newSize);
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
