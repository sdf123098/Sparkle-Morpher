package com.micaftic.morpher.core.gpu;

import com.micaftic.morpher.client.render.BuiltModel;
import com.micaftic.morpher.core.acceleration.AccelerationCapability;
import dev.spm.core.api.BoneMatrices;
import dev.spm.core.api.BonePose;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
    /**
     * The 25-float root pose and normal the native entry takes, reused across frames.
     *
     * <p>A thread-local rather than a field because the render thread is the only writer, and a
     * per-frame allocation here would defeat the point of the zero-copy path.
     */
    private static final ThreadLocal<float[]> ROOT_SCRATCH = ThreadLocal.withInitial(() -> new float[25]);

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
        // The native packed path writes the same 144-byte records straight into `out`, so it is
        // tried first and the Java writer below stays as the reference implementation and the
        // fallback. It returns false when the native library is absent, when the platform has no
        // native build, or when the frame is rejected — in every case the Java path then runs and
        // produces identical bytes.
        if (writeEvaluatedNative(cache, boneCount, rootPose, rootNormal, boneParams, stateBuffer,
                packedLight, out, floatTail)) {
            return true;
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

    /**
     * Writes the packed records through the native compiled-skeleton evaluator.
     *
     * <p>This is the fast path: the skeleton is compiled once per model, and each frame the native
     * side rebuilds the matrices and writes the 144-byte records straight into {@code out}, so a
     * frame allocates nothing and copies nothing. The Java writer below remains the reference
     * implementation and the fallback.
     *
     * <p>Returns {@code false} — never throws — when the native path cannot serve this frame, so
     * the caller can fall through to Java. That covers a missing library, a platform without a
     * native build, a skeleton the native side rejected, and a frame the native side refused.
     *
     * <p>{@code persistentOutput} is deliberately {@code false}: the native side remembers, per
     * compiled skeleton, that "the parameters did not change, so {@code out} is still correct", and
     * that memory cannot tell two different output buffers apart. The same model is written into
     * more than one buffer per frame depending on the render path, so claiming persistence here
     * would let one of them keep records from an older frame. A caller that owns a buffer across
     * frames has to opt in explicitly.
     *
     * @param floatTail {@code true} for the Blaze3D SSBO float tail, {@code false} for the OpenGL
     *     int tail
     */
    private static boolean writeEvaluatedNative(
            BuiltModel.ComputeCache cache,
            int boneCount,
            Matrix4f rootPose,
            Matrix3f rootNormal,
            float[] boneParams,
            float[] stateBuffer,
            int packedLight,
            ByteBuffer out,
            boolean floatTail
    ) {
        dev.spm.core.nativejni.NativePackedBoneEvaluator evaluator = cache.packedEvaluator();
        if (evaluator == null || evaluator.boneCount() != boneCount) {
            return false;
        }
        // The native entry writes little-endian records, so a big-endian view would be misread.
        if (out.order() != ByteOrder.LITTLE_ENDIAN) {
            return false;
        }
        // The root pose and normal are 25 floats: a 4x4 pose followed by a 3x3 normal matrix.
        float[] root = ROOT_SCRATCH.get();
        root[0] = rootPose.m00();
        root[1] = rootPose.m01();
        root[2] = rootPose.m02();
        root[3] = rootPose.m03();
        root[4] = rootPose.m10();
        root[5] = rootPose.m11();
        root[6] = rootPose.m12();
        root[7] = rootPose.m13();
        root[8] = rootPose.m20();
        root[9] = rootPose.m21();
        root[10] = rootPose.m22();
        root[11] = rootPose.m23();
        root[12] = rootPose.m30();
        root[13] = rootPose.m31();
        root[14] = rootPose.m32();
        root[15] = rootPose.m33();
        root[16] = rootNormal.m00();
        root[17] = rootNormal.m01();
        root[18] = rootNormal.m02();
        root[19] = rootNormal.m10();
        root[20] = rootNormal.m11();
        root[21] = rootNormal.m12();
        root[22] = rootNormal.m20();
        root[23] = rootNormal.m21();
        root[24] = rootNormal.m22();

        // Locators are only requested when the caller wants them, and the native side writes them
        // into a direct buffer that is then copied back for exactly the bones that asked.
        ByteBuffer state = stateBuffer == null ? null : cache.packedState(boneCount);
        int flags = (floatTail
                ? dev.spm.core.nativejni.NativePackedBoneEvaluator.FLOAT_TAIL
                : dev.spm.core.nativejni.NativePackedBoneEvaluator.INT_TAIL)
                | (dev.spm.core.nativejni.NativePackedBoneEvaluator.PASS_ALL << 1);
        try {
            evaluator.evaluate(root, boneParams, out, state, flags,
                    dev.spm.core.acceleration.Isa.AUTO, false, packedLight);
        } catch (RuntimeException rejected) {
            // A rejected frame leaves `out` untouched, so the Java path can still fill it.
            return false;
        }
        if (state != null) {
            copyLocators(state, boneParams, stateBuffer, boneCount);
        }
        return true;
    }

    /**
     * Copies the native locator results back into the caller's Java state array.
     *
     * <p>Only the bones that asked for a locator are touched, matching the Java writer: a bone
     * whose {@code boneParams[i*12+11]} is not {@code 1} leaves its slot alone, so a caller that
     * pre-fills the array keeps its values.
     */
    private static void copyLocators(ByteBuffer state, float[] boneParams, float[] stateBuffer,
                                     int boneCount) {
        for (int i = 0; i < boneCount; i++) {
            if (boneParams[i * 12 + 11] != 1.0f) {
                continue;
            }
            int off = i * 4;
            if (off + 2 >= stateBuffer.length) {
                continue;
            }
            stateBuffer[off] = state.getFloat(i * 16);
            stateBuffer[off + 1] = state.getFloat(i * 16 + 4);
            stateBuffer[off + 2] = state.getFloat(i * 16 + 8);
        }
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
