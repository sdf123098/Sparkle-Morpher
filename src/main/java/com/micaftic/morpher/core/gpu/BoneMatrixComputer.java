package com.micaftic.morpher.core.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * Computes the same 144-byte bone payload used by the 26.1.2 OpenGL world
 * renderer.  Modern HUD must use this exact payload so world and HUD do not
 * diverge in either animation math or GPU layout.
 */
public final class BoneMatrixComputer {
    public static final int FULL_BRIGHT_LIGHT = 0xF000F0;

    private static final Matrix4f identityScratch = new Matrix4f();
    private static final Matrix4f globalBoneScratch = new Matrix4f();
    private static final Matrix3f localNormalScratchMat = new Matrix3f();
    private static final Matrix3f globalNormalScratchMat = new Matrix3f();
    private static final float[] boneMatrix4Scratch = new float[16];
    private static final float[] boneMatrix3Scratch = new float[9];

    private static Matrix4f[] boneLocalScratch = new Matrix4f[0];
    private static boolean[] boneComputedScratch = new boolean[0];
    private static boolean[] boneVisibleScratch = new boolean[0];

    private BoneMatrixComputer() {
    }

    /** Writes one 144-byte SSBO record per bone: transform, normal, light and metadata. */
    public static boolean compute(GeoModel model, Matrix4f rootPose, Matrix3f rootNormal,
                                  float[] boneParams, float[] stateBuffer, int packedLight,
                                  ByteBuffer out) {
        int boneCount = model.bakedBones == null ? 0 : model.bakedBones.size();
        if (boneCount <= 0 || boneParams == null || boneParams.length < boneCount * 12) {
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

        out.clear();
        for (int i = 0; i < boneCount; i++) {
            GeoModel.BakedBone bone = model.bakedBones.get(i);
            if (!boneVisibleScratch[i]) {
                writeMatrix4(out, identityScratch.identity());
                writeIdentityNormal(out);
                out.putInt(0).putInt(1).putInt(0).putInt(0);
                continue;
            }

            Matrix4f localBoneMat = boneLocalScratch[i];
            globalBoneScratch.set(rootPose).mul(localBoneMat);
            writeMatrix4(out, globalBoneScratch);

            localBoneMat.normal(localNormalScratchMat);
            globalNormalScratchMat.set(rootNormal).mul(localNormalScratchMat);
            writeMatrix3AsMatrix4(out, globalNormalScratchMat);

            out.putInt(bone.glow ? FULL_BRIGHT_LIGHT : packedLight);
            out.putInt(0).putInt(0).putInt(0);
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

    private static void computeBoneLocalTransformLinear(int idx, List<GeoModel.BakedBone> bones,
                                                        float[] boneParams, float[] stateBuffer) {
        GeoModel.BakedBone bone = bones.get(idx);
        Matrix4f parentMatrix = identityScratch.identity();
        boolean isVisible = true;
        if (bone.parentIdx != -1) {
            parentMatrix = boneLocalScratch[bone.parentIdx];
            int parentOffset = bone.parentIdx * 12;
            if (!boneVisibleScratch[bone.parentIdx]
                    || boneParams[parentOffset + 10] == 1.0f) isVisible = false;
        }
        Matrix4f localMat = boneLocalScratch[idx];
        if (localMat == null) {
            localMat = new Matrix4f();
            boneLocalScratch[idx] = localMat;
        }
        localMat.set(parentMatrix);
        int p = idx * 12;
        float rx = boneParams[p], ry = boneParams[p + 1], rz = boneParams[p + 2];
        float tx = boneParams[p + 3], ty = boneParams[p + 4], tz = boneParams[p + 5];
        float sx = boneParams[p + 6], sy = boneParams[p + 7], sz = boneParams[p + 8];
        float hidden = boneParams[p + 9];
        if (sx == 0.0f || sy == 0.0f || sz == 0.0f || hidden == 1.0f) isVisible = false;
        localMat.translate((bone.pivotX - tx) * 0.0625f,
                (bone.pivotY + ty) * 0.0625f,
                (bone.pivotZ + tz) * 0.0625f);
        localMat.rotateZ(rz).rotateY(ry).rotateX(rx);
        if (sx != 1.0f || sy != 1.0f || sz != 1.0f) localMat.scale(sx, sy, sz);
        if (boneParams[p + 11] == 1.0F && stateBuffer != null && isVisible) {
            int s = idx * 4;
            if (s + 2 < stateBuffer.length) {
                stateBuffer[s] = -localMat.m30() * 16.0f;
                stateBuffer[s + 1] = localMat.m31() * 16.0f;
                stateBuffer[s + 2] = localMat.m32() * 16.0f;
            }
        }
        localMat.translate(-bone.pivotX / 16.0f, -bone.pivotY / 16.0f, -bone.pivotZ / 16.0f);
        boneVisibleScratch[idx] = isVisible;
    }

    private static Matrix4f computeBoneLocalTransform(int idx, List<GeoModel.BakedBone> bones,
                                                      float[] boneParams, float[] stateBuffer) {
        if (boneComputedScratch[idx]) return boneLocalScratch[idx];
        GeoModel.BakedBone bone = bones.get(idx);
        Matrix4f parentMatrix = identityScratch.identity();
        boolean isVisible = true;
        if (bone.parentIdx != -1) {
            parentMatrix = computeBoneLocalTransform(bone.parentIdx, bones, boneParams, stateBuffer);
            int parentOffset = bone.parentIdx * 12;
            if (!boneVisibleScratch[bone.parentIdx]
                    || boneParams[parentOffset + 10] == 1.0f) isVisible = false;
        }
        Matrix4f localMat = boneLocalScratch[idx];
        if (localMat == null) {
            localMat = new Matrix4f();
            boneLocalScratch[idx] = localMat;
        }
        localMat.set(parentMatrix);
        int p = idx * 12;
        float rx = boneParams[p], ry = boneParams[p + 1], rz = boneParams[p + 2];
        float tx = boneParams[p + 3], ty = boneParams[p + 4], tz = boneParams[p + 5];
        float sx = boneParams[p + 6], sy = boneParams[p + 7], sz = boneParams[p + 8];
        float hidden = boneParams[p + 9];
        if (sx == 0.0f || sy == 0.0f || sz == 0.0f || hidden == 1.0f) isVisible = false;
        localMat.translate((bone.pivotX - tx) * 0.0625f,
                (bone.pivotY + ty) * 0.0625f,
                (bone.pivotZ + tz) * 0.0625f);
        localMat.rotateZ(rz).rotateY(ry).rotateX(rx);
        if (sx != 1.0f || sy != 1.0f || sz != 1.0f) localMat.scale(sx, sy, sz);
        if (boneParams[p + 11] == 1.0F && stateBuffer != null && isVisible) {
            int s = idx * 4;
            if (s + 2 < stateBuffer.length) {
                stateBuffer[s] = -localMat.m30() * 16.0f;
                stateBuffer[s + 1] = localMat.m31() * 16.0f;
                stateBuffer[s + 2] = localMat.m32() * 16.0f;
            }
        }
        localMat.translate(-bone.pivotX / 16.0f, -bone.pivotY / 16.0f, -bone.pivotZ / 16.0f);
        boneVisibleScratch[idx] = isVisible;
        boneComputedScratch[idx] = true;
        return localMat;
    }

    private static void writeMatrix4(ByteBuffer out, Matrix4f matrix) {
        matrix.get(boneMatrix4Scratch);
        for (float value : boneMatrix4Scratch) out.putFloat(value);
    }

    private static void writeMatrix3AsMatrix4(ByteBuffer out, Matrix3f matrix) {
        matrix.get(boneMatrix3Scratch);
        out.putFloat(boneMatrix3Scratch[0]).putFloat(boneMatrix3Scratch[1]).putFloat(boneMatrix3Scratch[2]).putFloat(0.0f);
        out.putFloat(boneMatrix3Scratch[3]).putFloat(boneMatrix3Scratch[4]).putFloat(boneMatrix3Scratch[5]).putFloat(0.0f);
        out.putFloat(boneMatrix3Scratch[6]).putFloat(boneMatrix3Scratch[7]).putFloat(boneMatrix3Scratch[8]).putFloat(0.0f);
        out.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f).putFloat(1.0f);
    }

    private static void writeIdentityNormal(ByteBuffer out) {
        out.putFloat(1.0f).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
        out.putFloat(0.0f).putFloat(1.0f).putFloat(0.0f).putFloat(0.0f);
        out.putFloat(0.0f).putFloat(0.0f).putFloat(1.0f).putFloat(0.0f);
        out.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f).putFloat(1.0f);
    }
}

