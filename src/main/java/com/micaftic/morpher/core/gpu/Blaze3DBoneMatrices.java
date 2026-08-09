package com.micaftic.morpher.core.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

final class Blaze3DBoneMatrices {
    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;
    private static final float[] matrix4Scratch = new float[16];
    private static final float[] matrix3Scratch = new float[9];
    private static final Matrix4f identityScratch = new Matrix4f();
    private static final Matrix4f globalBoneScratch = new Matrix4f();
    private static final Matrix3f localNormalScratch = new Matrix3f();
    private static final Matrix3f globalNormalScratch = new Matrix3f();

    private static Matrix4f[] boneLocalScratch = new Matrix4f[0];
    private static boolean[] boneComputedScratch = new boolean[0];
    private static boolean[] boneVisibleScratch = new boolean[0];

    private Blaze3DBoneMatrices() {
    }

    static boolean write(
            GeoModel model,
            Matrix4f rootPose,
            Matrix3f rootNormal,
            float[] boneParams,
            float[] stateBuffer,
            int packedLight,
            ByteBuffer out
    ) {
        int boneCount = model.bakedBones == null ? 0 : model.bakedBones.size();
        if (boneCount <= 0 || boneParams == null || boneParams.length < boneCount * 12) {
            return false;
        }

        ensureBoneScratch(boneCount);
        Arrays.fill(boneVisibleScratch, 0, boneCount, false);
        out.clear();

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
            if (!boneVisibleScratch[i]) {
                writeMatrix4(out, identityScratch.identity());
                writeIdentityNormal(out);
                out.putFloat(0.0f);
                out.putFloat(1.0f);
                out.putFloat(0.0f);
                out.putFloat(0.0f);
                continue;
            }

            Matrix4f localBoneMat = boneLocalScratch[i];
            globalBoneScratch.set(rootPose).mul(localBoneMat);
            writeMatrix4(out, globalBoneScratch);

            localBoneMat.normal(localNormalScratch);
            globalNormalScratch.set(rootNormal).mul(localNormalScratch);
            writeMatrix3AsMatrix4(out, globalNormalScratch);

            out.putFloat((float) (bone.glow ? FULL_BRIGHT_LIGHT : packedLight));
            out.putFloat(0.0f);
            out.putFloat(0.0f);
            out.putFloat(0.0f);
        }

        out.flip();
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
        float unk1 = boneParams[pOffset + 9];
        float unk2 = boneParams[pOffset + 10];
        float unk3 = boneParams[pOffset + 11];

        if ((animSx == 0.0f || animSy == 0.0f || animSz == 0.0f) || unk1 == 1.0f || unk2 == 1.0f) {
            isVisible = false;
        }

        localMat.translate((bone.pivotX - animTx) * 0.0625f, (bone.pivotY + animTy) * 0.0625f, (bone.pivotZ + animTz) * 0.0625f);
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
        float unk1 = boneParams[pOffset + 9];
        float unk2 = boneParams[pOffset + 10];
        float unk3 = boneParams[pOffset + 11];

        if ((animSx == 0.0f || animSy == 0.0f || animSz == 0.0f) || unk1 == 1.0f || unk2 == 1.0f) {
            isVisible = false;
        }

        localMat.translate((bone.pivotX - animTx) * 0.0625f, (bone.pivotY + animTy) * 0.0625f, (bone.pivotZ + animTz) * 0.0625f);
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
        matrix.get(matrix4Scratch);
        for (int i = 0; i < 16; i++) {
            out.putFloat(matrix4Scratch[i]);
        }
    }

    private static void writeMatrix3AsMatrix4(ByteBuffer out, Matrix3f matrix) {
        matrix.get(matrix3Scratch);
        out.putFloat(matrix3Scratch[0]);
        out.putFloat(matrix3Scratch[1]);
        out.putFloat(matrix3Scratch[2]);
        out.putFloat(0.0f);
        out.putFloat(matrix3Scratch[3]);
        out.putFloat(matrix3Scratch[4]);
        out.putFloat(matrix3Scratch[5]);
        out.putFloat(0.0f);
        out.putFloat(matrix3Scratch[6]);
        out.putFloat(matrix3Scratch[7]);
        out.putFloat(matrix3Scratch[8]);
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
}
