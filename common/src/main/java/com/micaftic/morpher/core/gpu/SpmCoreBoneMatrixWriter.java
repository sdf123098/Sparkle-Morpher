package com.micaftic.morpher.core.gpu;

import com.micaftic.morpher.client.render.BuiltModel;
import com.micaftic.morpher.core.acceleration.AccelerationCapability;
import dev.spm.core.api.BoneMatrices;
import dev.spm.core.api.BonePose;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Shared spm-core backed bone hierarchy evaluator and GPU buffer serializer.
 *
 * <p>Replaces host-side manual bone traversal in {@link Blaze3DBoneMatrices} and
 * {@link GpuRenderPath} with {@code spm-core} ({@code CpuComputeProvider.evaluateBones}).</p>
 */
public final class SpmCoreBoneMatrixWriter {
    public static final int FULL_BRIGHT_LIGHT = 0xF000F0;

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private SpmCoreBoneMatrixWriter() {
    }

    public static BonePose toBonePose(List<BuiltModel.BakedBone> bones, float[] boneParams) {
        int n = bones.size();
        float[] rotations = new float[n * 3];
        float[] translations = new float[n * 3];
        float[] scales = new float[n * 3];
        boolean[] ownHidden = new boolean[n];
        boolean[] childrenHidden = new boolean[n];
        int[] glow = new int[n];
        for (int i = 0; i < n; i++) {
            int p = i * 12;
            int v = i * 3;
            rotations[v] = boneParams[p];
            rotations[v + 1] = boneParams[p + 1];
            rotations[v + 2] = boneParams[p + 2];
            translations[v] = -boneParams[p + 3] * 0.0625f;
            translations[v + 1] = boneParams[p + 4] * 0.0625f;
            translations[v + 2] = boneParams[p + 5] * 0.0625f;
            scales[v] = boneParams[p + 6];
            scales[v + 1] = boneParams[p + 7];
            scales[v + 2] = boneParams[p + 8];
            boolean hidden = boneParams[p + 9] == 1.0f || boneParams[p + 10] == 1.0f;
            ownHidden[i] = hidden;
            childrenHidden[i] = hidden;
            glow[i] = bones.get(i).glow ? 15 : 255;
        }
        return new BonePose(rotations, translations, scales, ownHidden, childrenHidden, glow);
    }

    public static void updateLocatorStateBuffer(
            List<BuiltModel.BakedBone> bones,
            float[] boneParams,
            BoneMatrices matrices,
            float[] stateBuffer
    ) {
        if (stateBuffer == null || matrices == null) {
            return;
        }
        float[] locators = matrices.locatorPositions();
        boolean[] visible = matrices.visible();
        int boneCount = bones.size();
        for (int i = 0; i < boneCount; i++) {
            if (boneParams[i * 12 + 11] == 1.0f && visible[i]) {
                int off = i * 4;
                if (off + 2 < stateBuffer.length) {
                    stateBuffer[off] = -locators[i * 3] * 16.0f;
                    stateBuffer[off + 1] = locators[i * 3 + 1] * 16.0f;
                    stateBuffer[off + 2] = locators[i * 3 + 2] * 16.0f;
                }
            }
        }
    }

    /**
     * Evaluates local bone matrices and visibility through {@code spm-core} for CPU vertex rendering fallback.
     */
    public static boolean evaluateBoneLocalTransforms(
            BuiltModel model,
            float[] boneParams,
            float[] stateBuffer,
            Matrix4f[] boneLocalTransforms,
            boolean[] boneVisible
    ) {
        int boneCount = model == null || model.bakedBones == null ? 0 : model.bakedBones.size();
        if (boneCount <= 0
                || boneParams == null
                || boneParams.length < boneCount * 12
                || boneLocalTransforms == null
                || boneLocalTransforms.length < boneCount
                || boneVisible == null
                || boneVisible.length < boneCount) {
            return false;
        }
        BuiltModel.ComputeCache cache = model.ensureComputeCache();
        if (cache == null) {
            return false;
        }
        BoneMatrices matrices;
        try {
            BonePose pose = toBonePose(model.bakedBones, boneParams);
            matrices = AccelerationCapability.computeProvider().evaluateBones(cache.skeleton, pose);
        } catch (Throwable failure) {
            return false;
        }

        updateLocatorStateBuffer(model.bakedBones, boneParams, matrices, stateBuffer);

        float[] transforms = matrices.transforms();
        boolean[] visible = matrices.visible();
        for (int i = 0; i < boneCount; i++) {
            boneVisible[i] = visible[i];
            if (!visible[i]) {
                continue;
            }
            Matrix4f localMat = boneLocalTransforms[i];
            if (localMat == null) {
                localMat = new Matrix4f();
                boneLocalTransforms[i] = localMat;
            }
            int mOff = i * 16;
            localMat.set(
                    transforms[mOff], transforms[mOff + 1], transforms[mOff + 2], transforms[mOff + 3],
                    transforms[mOff + 4], transforms[mOff + 5], transforms[mOff + 6], transforms[mOff + 7],
                    transforms[mOff + 8], transforms[mOff + 9], transforms[mOff + 10], transforms[mOff + 11],
                    transforms[mOff + 12], transforms[mOff + 13], transforms[mOff + 14], transforms[mOff + 15]
            );
        }
        return true;
    }

    /**
     * Evaluates local bone matrices through {@code spm-core} and writes 144B/bone GPU records.
     *
     * @param floatTail {@code true} for Blaze3D SSBO float layout; {@code false} for OpenGL int tail layout.
     */
    public static boolean writeEvaluated(
            BuiltModel model,
            Matrix4f rootPose,
            Matrix3f rootNormal,
            float[] boneParams,
            float[] stateBuffer,
            int packedLight,
            ByteBuffer out,
            boolean floatTail
    ) {
        int boneCount = model == null || model.bakedBones == null ? 0 : model.bakedBones.size();
        if (boneCount <= 0 || boneParams == null || boneParams.length < boneCount * 12 || out == null) {
            return false;
        }
        BuiltModel.ComputeCache cache = model.ensureComputeCache();
        if (cache == null) {
            return false;
        }
        BoneMatrices matrices;
        try {
            BonePose pose = toBonePose(model.bakedBones, boneParams);
            matrices = AccelerationCapability.computeProvider().evaluateBones(cache.skeleton, pose);
        } catch (Throwable failure) {
            return false;
        }

        updateLocatorStateBuffer(model.bakedBones, boneParams, matrices, stateBuffer);

        out.clear();
        Scratch scratch = SCRATCH.get();
        float[] transforms = matrices.transforms();
        float[] normals = matrices.normals();
        boolean[] visible = matrices.visible();

        for (int i = 0; i < boneCount; i++) {
            BuiltModel.BakedBone bone = model.bakedBones.get(i);
            if (!visible[i]) {
                writeMatrix4(out, scratch.identity.identity(), scratch.matrix4);
                writeIdentityNormal(out);
                writeTail(out, 0, true, floatTail);
                continue;
            }

            int mOff = i * 16;
            scratch.localBone.set(
                    transforms[mOff], transforms[mOff + 1], transforms[mOff + 2], transforms[mOff + 3],
                    transforms[mOff + 4], transforms[mOff + 5], transforms[mOff + 6], transforms[mOff + 7],
                    transforms[mOff + 8], transforms[mOff + 9], transforms[mOff + 10], transforms[mOff + 11],
                    transforms[mOff + 12], transforms[mOff + 13], transforms[mOff + 14], transforms[mOff + 15]
            );
            scratch.globalBone.set(rootPose).mul(scratch.localBone);
            writeMatrix4(out, scratch.globalBone, scratch.matrix4);

            int nOff = i * 9;
            scratch.localNormal.set(
                    normals[nOff], normals[nOff + 1], normals[nOff + 2],
                    normals[nOff + 3], normals[nOff + 4], normals[nOff + 5],
                    normals[nOff + 6], normals[nOff + 7], normals[nOff + 8]
            );
            scratch.globalNormal.set(rootNormal).mul(scratch.localNormal);
            writeMatrix3AsMatrix4(out, scratch.globalNormal, scratch.matrix3);

            int light = bone.glow ? FULL_BRIGHT_LIGHT : packedLight;
            writeTail(out, light, false, floatTail);
        }

        out.flip();
        return true;
    }

    private static void writeTail(ByteBuffer out, int light, boolean hidden, boolean floatTail) {
        if (floatTail) {
            out.putFloat((float) light);
            out.putFloat(hidden ? 1.0f : 0.0f);
            out.putFloat(0.0f);
            out.putFloat(0.0f);
        } else {
            out.putInt(light);
            out.putInt(hidden ? 1 : 0);
            out.putInt(0);
            out.putInt(0);
        }
    }

    private static void writeMatrix4(ByteBuffer out, Matrix4f matrix, float[] tmp) {
        matrix.get(tmp);
        for (int i = 0; i < 16; i++) {
            out.putFloat(tmp[i]);
        }
    }

    private static void writeMatrix3AsMatrix4(ByteBuffer out, Matrix3f matrix, float[] tmp) {
        matrix.get(tmp);
        out.putFloat(tmp[0]);
        out.putFloat(tmp[1]);
        out.putFloat(tmp[2]);
        out.putFloat(0.0f);
        out.putFloat(tmp[3]);
        out.putFloat(tmp[4]);
        out.putFloat(tmp[5]);
        out.putFloat(0.0f);
        out.putFloat(tmp[6]);
        out.putFloat(tmp[7]);
        out.putFloat(tmp[8]);
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

    private static final class Scratch {
        final float[] matrix4 = new float[16];
        final float[] matrix3 = new float[9];
        final Matrix4f identity = new Matrix4f();
        final Matrix4f localBone = new Matrix4f();
        final Matrix4f globalBone = new Matrix4f();
        final Matrix3f localNormal = new Matrix3f();
        final Matrix3f globalNormal = new Matrix3f();
    }
}
