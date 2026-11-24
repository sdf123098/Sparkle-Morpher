package com.micaftic.morpher.core.gpu;

import com.micaftic.morpher.client.render.BuiltModel;
import dev.spm.core.api.BoneMatrices;
import dev.spm.core.api.BonePose;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Shared zero-allocation bone hierarchy evaluator and GPU buffer serializer matching
 * {@code spm-core} ({@code dev.spm.core.render.cpu.BoneEvaluator}) math.
 */
public final class SpmCoreBoneMatrixWriter {
    public static final int FULL_BRIGHT_LIGHT = 0xF000F0;
    public static final int BONE_RECORD_BYTES = 144;

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    /**
     * The 25-float root pose and normal the native entry takes, reused across frames.
     *
     * <p>A thread-local rather than a field because the render thread is the only writer, and a
     * per-frame allocation here would defeat the point of the zero-copy path.
     */
    private static final ThreadLocal<float[]> ROOT_SCRATCH = ThreadLocal.withInitial(() -> new float[25]);
    private static final byte[] HIDDEN_RECORD_FLOAT_TAIL_LE = buildHiddenRecord(true);
    private static final byte[] HIDDEN_RECORD_INT_TAIL_LE = buildHiddenRecord(false);

    /**
     * Thread-local zero-allocation snapshot of evaluated local bone matrices, inverse-transpose
     * normal matrices, and visibility flags. Valid until the next evaluation on the same thread.
     */
    public static final class EvaluatedBones {
        public float[] transforms;
        public float[] normals;
        public boolean[] visible;
        public int boneCount;
    }

    private SpmCoreBoneMatrixWriter() {
    }

    private static byte[] buildHiddenRecord(boolean floatTail) {
        ByteBuffer buf = ByteBuffer.allocate(BONE_RECORD_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        // Identity 4x4 transform
        buf.putFloat(1.0f); buf.putFloat(0.0f); buf.putFloat(0.0f); buf.putFloat(0.0f);
        buf.putFloat(0.0f); buf.putFloat(1.0f); buf.putFloat(0.0f); buf.putFloat(0.0f);
        buf.putFloat(0.0f); buf.putFloat(0.0f); buf.putFloat(1.0f); buf.putFloat(0.0f);
        buf.putFloat(0.0f); buf.putFloat(0.0f); buf.putFloat(0.0f); buf.putFloat(1.0f);
        // Identity 4x4 normal
        buf.putFloat(1.0f); buf.putFloat(0.0f); buf.putFloat(0.0f); buf.putFloat(0.0f);
        buf.putFloat(0.0f); buf.putFloat(1.0f); buf.putFloat(0.0f); buf.putFloat(0.0f);
        buf.putFloat(0.0f); buf.putFloat(0.0f); buf.putFloat(1.0f); buf.putFloat(0.0f);
        buf.putFloat(0.0f); buf.putFloat(0.0f); buf.putFloat(0.0f); buf.putFloat(1.0f);
        if (floatTail) {
            buf.putFloat(0.0f);
            buf.putFloat(1.0f);
            buf.putFloat(0.0f);
            buf.putFloat(0.0f);
        } else {
            buf.putInt(0);
            buf.putInt(1);
            buf.putInt(0);
            buf.putInt(0);
        }
        return buf.array();
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
            ownHidden[i] = boneParams[p + 9] != 0.0f;
            childrenHidden[i] = boneParams[p + 10] != 0.0f;
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
     * Evaluates local bone matrices and visibility in place using {@link BuiltModel.ComputeCache}
     * topology and {@code spm-core} rigid bone math without per-frame array allocations.
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
        Scratch scratch = SCRATCH.get();
        if (!evaluateInPlace(cache, boneParams, stateBuffer, scratch, false)) {
            return false;
        }

        float[] transforms = scratch.transforms;
        boolean[] visible = scratch.visible;
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
     * Evaluates local bone matrices, inverse-transpose normal matrices, and visibility into a
     * zero-allocation thread-local snapshot for CPU vertex rendering.
     */
    public static EvaluatedBones evaluateForCpuRender(
            BuiltModel model,
            float[] boneParams,
            float[] stateBuffer
    ) {
        int boneCount = model == null || model.bakedBones == null ? 0 : model.bakedBones.size();
        if (boneCount <= 0 || boneParams == null || boneParams.length < boneCount * 12) {
            return null;
        }
        BuiltModel.ComputeCache cache = model.ensureComputeCache();
        if (cache == null) {
            return null;
        }
        Scratch scratch = SCRATCH.get();
        if (!evaluateInPlace(cache, boneParams, stateBuffer, scratch, true)) {
            return null;
        }
        EvaluatedBones view = scratch.view;
        view.transforms = scratch.transforms;
        view.normals = scratch.normals;
        view.visible = scratch.visible;
        view.boneCount = boneCount;
        return view;
    }

    /**
     * Evaluates local bone matrices in place and writes 144B/bone GPU records directly into {@code out}
     * without intermediate {@link Matrix4f}/{@link Matrix3f} object staging.
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
        if (boneCount <= 0
                || rootPose == null
                || rootNormal == null
                || boneParams == null
                || boneParams.length < boneCount * 12
                || out == null
                || out.capacity() < boneCount * BONE_RECORD_BYTES) {
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
        Scratch scratch = SCRATCH.get();
        if (!evaluateInPlace(cache, boneParams, stateBuffer, scratch, true)) {
            return false;
        }

        out.clear();
        float[] transforms = scratch.transforms;
        float[] normals = scratch.normals;
        boolean[] visible = scratch.visible;
        List<BuiltModel.BakedBone> bakedBones = model.bakedBones;

        float r00 = rootPose.m00(), r01 = rootPose.m01(), r02 = rootPose.m02(), r03 = rootPose.m03();
        float r10 = rootPose.m10(), r11 = rootPose.m11(), r12 = rootPose.m12(), r13 = rootPose.m13();
        float r20 = rootPose.m20(), r21 = rootPose.m21(), r22 = rootPose.m22(), r23 = rootPose.m23();
        float r30 = rootPose.m30(), r31 = rootPose.m31(), r32 = rootPose.m32(), r33 = rootPose.m33();
        boolean affineRoot = r03 == 0.0f && r13 == 0.0f && r23 == 0.0f && r33 == 1.0f;

        float rn00 = rootNormal.m00(), rn01 = rootNormal.m01(), rn02 = rootNormal.m02();
        float rn10 = rootNormal.m10(), rn11 = rootNormal.m11(), rn12 = rootNormal.m12();
        float rn20 = rootNormal.m20(), rn21 = rootNormal.m21(), rn22 = rootNormal.m22();

        boolean littleEndian = out.order() == ByteOrder.LITTLE_ENDIAN;
        byte[] hiddenRecord = floatTail ? HIDDEN_RECORD_FLOAT_TAIL_LE : HIDDEN_RECORD_INT_TAIL_LE;

        for (int i = 0; i < boneCount; i++) {
            if (!visible[i]) {
                if (littleEndian) {
                    out.put(hiddenRecord);
                } else {
                    writeHiddenBoneFallback(out, floatTail);
                }
                continue;
            }

            int mOff = i * 16;
            float l00 = transforms[mOff],     l01 = transforms[mOff + 1],  l02 = transforms[mOff + 2];
            float l10 = transforms[mOff + 4], l11 = transforms[mOff + 5],  l12 = transforms[mOff + 6];
            float l20 = transforms[mOff + 8], l21 = transforms[mOff + 9],  l22 = transforms[mOff + 10];
            float l30 = transforms[mOff + 12], l31 = transforms[mOff + 13], l32 = transforms[mOff + 14];

            if (affineRoot) {
                out.putFloat(r00 * l00 + r10 * l01 + r20 * l02);
                out.putFloat(r01 * l00 + r11 * l01 + r21 * l02);
                out.putFloat(r02 * l00 + r12 * l01 + r22 * l02);
                out.putFloat(0.0f);

                out.putFloat(r00 * l10 + r10 * l11 + r20 * l12);
                out.putFloat(r01 * l10 + r11 * l11 + r21 * l12);
                out.putFloat(r02 * l10 + r12 * l11 + r22 * l12);
                out.putFloat(0.0f);

                out.putFloat(r00 * l20 + r10 * l21 + r20 * l22);
                out.putFloat(r01 * l20 + r11 * l21 + r21 * l22);
                out.putFloat(r02 * l20 + r12 * l21 + r22 * l22);
                out.putFloat(0.0f);

                out.putFloat(r00 * l30 + r10 * l31 + r20 * l32 + r30);
                out.putFloat(r01 * l30 + r11 * l31 + r21 * l32 + r31);
                out.putFloat(r02 * l30 + r12 * l31 + r22 * l32 + r32);
                out.putFloat(1.0f);
            } else {
                out.putFloat(r00 * l00 + r10 * l01 + r20 * l02);
                out.putFloat(r01 * l00 + r11 * l01 + r21 * l02);
                out.putFloat(r02 * l00 + r12 * l01 + r22 * l02);
                out.putFloat(r03 * l00 + r13 * l01 + r23 * l02);

                out.putFloat(r00 * l10 + r10 * l11 + r20 * l12);
                out.putFloat(r01 * l10 + r11 * l11 + r21 * l12);
                out.putFloat(r02 * l10 + r12 * l11 + r22 * l12);
                out.putFloat(r03 * l10 + r13 * l11 + r23 * l12);

                out.putFloat(r00 * l20 + r10 * l21 + r20 * l22);
                out.putFloat(r01 * l20 + r11 * l21 + r21 * l22);
                out.putFloat(r02 * l20 + r12 * l21 + r22 * l22);
                out.putFloat(r03 * l20 + r13 * l21 + r23 * l22);

                out.putFloat(r00 * l30 + r10 * l31 + r20 * l32 + r30);
                out.putFloat(r01 * l30 + r11 * l31 + r21 * l32 + r31);
                out.putFloat(r02 * l30 + r12 * l31 + r22 * l32 + r32);
                out.putFloat(r03 * l30 + r13 * l31 + r23 * l32 + r33);
            }

            int nOff = i * 9;
            float ln00 = normals[nOff],     ln01 = normals[nOff + 1], ln02 = normals[nOff + 2];
            float ln10 = normals[nOff + 3], ln11 = normals[nOff + 4], ln12 = normals[nOff + 5];
            float ln20 = normals[nOff + 6], ln21 = normals[nOff + 7], ln22 = normals[nOff + 8];

            out.putFloat(rn00 * ln00 + rn10 * ln01 + rn20 * ln02);
            out.putFloat(rn01 * ln00 + rn11 * ln01 + rn21 * ln02);
            out.putFloat(rn02 * ln00 + rn12 * ln01 + rn22 * ln02);
            out.putFloat(0.0f);

            out.putFloat(rn00 * ln10 + rn10 * ln11 + rn20 * ln12);
            out.putFloat(rn01 * ln10 + rn11 * ln11 + rn21 * ln12);
            out.putFloat(rn02 * ln10 + rn12 * ln11 + rn22 * ln12);
            out.putFloat(0.0f);

            out.putFloat(rn00 * ln20 + rn10 * ln21 + rn20 * ln22);
            out.putFloat(rn01 * ln20 + rn11 * ln21 + rn21 * ln22);
            out.putFloat(rn02 * ln20 + rn12 * ln21 + rn22 * ln22);
            out.putFloat(0.0f);

            out.putFloat(0.0f);
            out.putFloat(0.0f);
            out.putFloat(0.0f);
            out.putFloat(1.0f);

            int light = bakedBones.get(i).glow ? FULL_BRIGHT_LIGHT : packedLight;
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
                    dev.spm.core.acceleration.Isa.AUTO, true, packedLight);
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

    /**
     * Zero-allocation rigid hierarchy evaluation matching {@code dev.spm.core.render.cpu.BoneEvaluator}.
     * Uses exact fast paths for zero-rotation/unit-scale bones and orthogonal/uniform-scale normal
     * matrices while falling back to full double-precision Euler and cofactor inversion otherwise.
     */
    private static boolean evaluateInPlace(
            BuiltModel.ComputeCache cache,
            float[] boneParams,
            float[] stateBuffer,
            Scratch scratch,
            boolean computeNormals
    ) {
        int[] order = cache.evaluationOrder;
        int[] parents = cache.parents;
        float[] pivots = cache.pivots;
        int n = parents.length;
        scratch.ensureCapacity(n, computeNormals);

        float[] transforms = scratch.transforms;
        float[] normals = scratch.normals;
        boolean[] visible = scratch.visible;
        boolean[] descendants = scratch.descendants;
        double[] uniformScale = scratch.uniformScale;

        for (int idx = 0; idx < n; idx++) {
            int bone = order[idx];
            visible[bone] = false;
            descendants[bone] = false;
            uniformScale[bone] = 0.0;
            int parent = parents[bone];
            if (parent >= 0 && !descendants[parent]) {
                continue;
            }
            int p = bone * 12;
            float sxScale = boneParams[p + 6];
            float syScale = boneParams[p + 7];
            float szScale = boneParams[p + 8];
            if (sxScale == 0.0f || syScale == 0.0f || szScale == 0.0f) {
                continue;
            }
            float rx = boneParams[p];
            float ry = boneParams[p + 1];
            float rz = boneParams[p + 2];
            double dx = -boneParams[p + 3] * 0.0625;
            double dy = boneParams[p + 4] * 0.0625;
            double dz = boneParams[p + 5] * 0.0625;

            boolean uniformLocal = sxScale == syScale && syScale == szScale;
            boolean unitScale = uniformLocal && sxScale == 1.0f;

            int mOff = bone * 16;
            float w00, w10, w20, w01, w11, w21, w02, w12, w22, w03, w13, w23;
            if (rx == 0.0f && ry == 0.0f && rz == 0.0f && unitScale) {
                if (parent < 0) {
                    w00 = 1.0f; w10 = 0.0f; w20 = 0.0f;
                    w01 = 0.0f; w11 = 1.0f; w21 = 0.0f;
                    w02 = 0.0f; w12 = 0.0f; w22 = 1.0f;
                    w03 = (float) dx; w13 = (float) dy; w23 = (float) dz;
                } else {
                    int pOff = parent * 16;
                    w00 = transforms[pOff];     w10 = transforms[pOff + 1];  w20 = transforms[pOff + 2];
                    w01 = transforms[pOff + 4]; w11 = transforms[pOff + 5];  w21 = transforms[pOff + 6];
                    w02 = transforms[pOff + 8]; w12 = transforms[pOff + 9];  w22 = transforms[pOff + 10];
                    if (dx == 0.0 && dy == 0.0 && dz == 0.0) {
                        w03 = transforms[pOff + 12];
                        w13 = transforms[pOff + 13];
                        w23 = transforms[pOff + 14];
                    } else {
                        w03 = (float) ((double) w00 * dx + (double) w01 * dy + (double) w02 * dz + transforms[pOff + 12]);
                        w13 = (float) ((double) w10 * dx + (double) w11 * dy + (double) w12 * dz + transforms[pOff + 13]);
                        w23 = (float) ((double) w20 * dx + (double) w21 * dy + (double) w22 * dz + transforms[pOff + 14]);
                    }
                }
            } else {
                double cx = rx == 0.0f ? 1.0 : Math.cos(rx);
                double sx = rx == 0.0f ? 0.0 : Math.sin(rx);
                double cy = ry == 0.0f ? 1.0 : Math.cos(ry);
                double sy = ry == 0.0f ? 0.0 : Math.sin(ry);
                double cz = rz == 0.0f ? 1.0 : Math.cos(rz);
                double sz = rz == 0.0f ? 0.0 : Math.sin(rz);

                // Column-major 3x3 linear part (Z * Y * X) * diag(sxScale, syScale, szScale)
                float l00 = (float) ((cz * cy) * sxScale);
                float l10 = (float) ((sz * cy) * sxScale);
                float l20 = (float) ((-sy) * sxScale);
                float l01 = (float) ((cz * sy * sx - sz * cx) * syScale);
                float l11 = (float) ((sz * sy * sx + cz * cx) * syScale);
                float l21 = (float) ((cy * sx) * syScale);
                float l02 = (float) ((cz * sy * cx + sz * sx) * szScale);
                float l12 = (float) ((sz * sy * cx - cz * sx) * szScale);
                float l22 = (float) ((cy * cx) * szScale);

                int pv = bone * 3;
                double px = pivots[pv];
                double py = pivots[pv + 1];
                double pz = pivots[pv + 2];

                float l03 = (float) (px + dx - ((double) l00 * px + (double) l01 * py + (double) l02 * pz));
                float l13 = (float) (py + dy - ((double) l10 * px + (double) l11 * py + (double) l12 * pz));
                float l23 = (float) (pz + dz - ((double) l20 * px + (double) l21 * py + (double) l22 * pz));

                if (parent < 0) {
                    w00 = l00; w10 = l10; w20 = l20;
                    w01 = l01; w11 = l11; w21 = l21;
                    w02 = l02; w12 = l12; w22 = l22;
                    w03 = l03; w13 = l13; w23 = l23;
                } else {
                    int pOff = parent * 16;
                    double p00 = transforms[pOff],     p10 = transforms[pOff + 1],  p20 = transforms[pOff + 2];
                    double p01 = transforms[pOff + 4], p11 = transforms[pOff + 5],  p21 = transforms[pOff + 6];
                    double p02 = transforms[pOff + 8], p12 = transforms[pOff + 9],  p22 = transforms[pOff + 10];
                    double p03 = transforms[pOff + 12], p13 = transforms[pOff + 13], p23 = transforms[pOff + 14];

                    w00 = (float) (p00 * l00 + p01 * l10 + p02 * l20);
                    w10 = (float) (p10 * l00 + p11 * l10 + p12 * l20);
                    w20 = (float) (p20 * l00 + p21 * l10 + p22 * l20);

                    w01 = (float) (p00 * l01 + p01 * l11 + p02 * l21);
                    w11 = (float) (p10 * l01 + p11 * l11 + p12 * l21);
                    w21 = (float) (p20 * l01 + p21 * l11 + p22 * l21);

                    w02 = (float) (p00 * l02 + p01 * l12 + p02 * l22);
                    w12 = (float) (p10 * l02 + p11 * l12 + p12 * l22);
                    w22 = (float) (p20 * l02 + p21 * l12 + p22 * l22);

                    w03 = (float) (p00 * l03 + p01 * l13 + p02 * l23 + p03);
                    w13 = (float) (p10 * l03 + p11 * l13 + p12 * l23 + p13);
                    w23 = (float) (p20 * l03 + p21 * l13 + p22 * l23 + p23);
                }
            }

            if (!Float.isFinite(w00) || !Float.isFinite(w10) || !Float.isFinite(w20)
                    || !Float.isFinite(w01) || !Float.isFinite(w11) || !Float.isFinite(w21)
                    || !Float.isFinite(w02) || !Float.isFinite(w12) || !Float.isFinite(w22)
                    || !Float.isFinite(w03) || !Float.isFinite(w13) || !Float.isFinite(w23)) {
                return false;
            }

            transforms[mOff] = w00;      transforms[mOff + 1] = w10;  transforms[mOff + 2] = w20;  transforms[mOff + 3] = 0.0f;
            transforms[mOff + 4] = w01;  transforms[mOff + 5] = w11;  transforms[mOff + 6] = w21;  transforms[mOff + 7] = 0.0f;
            transforms[mOff + 8] = w02;  transforms[mOff + 9] = w12;  transforms[mOff + 10] = w22; transforms[mOff + 11] = 0.0f;
            transforms[mOff + 12] = w03; transforms[mOff + 13] = w13; transforms[mOff + 14] = w23; transforms[mOff + 15] = 1.0f;

            double worldUniformScale = 0.0;
            if (uniformLocal) {
                double baseScale = parent < 0 ? 1.0 : uniformScale[parent];
                if (baseScale != 0.0) {
                    double candidate = baseScale * sxScale;
                    double abs = Math.abs(candidate);
                    if (Double.isFinite(candidate) && abs >= 1e-6 && abs <= 1e6) {
                        worldUniformScale = candidate;
                    }
                }
            }
            uniformScale[bone] = worldUniformScale;

            if (computeNormals) {
                int nOff = bone * 9;
                if (worldUniformScale == 1.0 || worldUniformScale == -1.0) {
                    normals[nOff] = w00;     normals[nOff + 1] = w10; normals[nOff + 2] = w20;
                    normals[nOff + 3] = w01; normals[nOff + 4] = w11; normals[nOff + 5] = w21;
                    normals[nOff + 6] = w02; normals[nOff + 7] = w12; normals[nOff + 8] = w22;
                } else if (worldUniformScale != 0.0) {
                    double invS2 = 1.0 / (worldUniformScale * worldUniformScale);
                    normals[nOff] = (float) (w00 * invS2);     normals[nOff + 1] = (float) (w10 * invS2); normals[nOff + 2] = (float) (w20 * invS2);
                    normals[nOff + 3] = (float) (w01 * invS2); normals[nOff + 4] = (float) (w11 * invS2); normals[nOff + 5] = (float) (w21 * invS2);
                    normals[nOff + 6] = (float) (w02 * invS2); normals[nOff + 7] = (float) (w12 * invS2); normals[nOff + 8] = (float) (w22 * invS2);
                } else {
                    double a = w00, b = w01, c = w02;
                    double d = w10, e = w11, f = w12;
                    double g = w20, h = w21, ii = w22;
                    double det = a * (e * ii - f * h) - b * (d * ii - f * g) + c * (d * h - e * g);
                    if (det == 0.0 || !Double.isFinite(det)) {
                        return false;
                    }
                    double invDet = 1.0 / det;
                    float n0 = (float) ((e * ii - f * h) * invDet);
                    float n1 = (float) ((c * h - b * ii) * invDet);
                    float n2 = (float) ((b * f - c * e) * invDet);
                    float n3 = (float) ((f * g - d * ii) * invDet);
                    float n4 = (float) ((a * ii - c * g) * invDet);
                    float n5 = (float) ((c * d - a * f) * invDet);
                    float n6 = (float) ((d * h - e * g) * invDet);
                    float n7 = (float) ((b * g - a * h) * invDet);
                    float n8 = (float) ((a * e - b * d) * invDet);
                    if (!Float.isFinite(n0) || !Float.isFinite(n1) || !Float.isFinite(n2)
                            || !Float.isFinite(n3) || !Float.isFinite(n4) || !Float.isFinite(n5)
                            || !Float.isFinite(n6) || !Float.isFinite(n7) || !Float.isFinite(n8)) {
                        return false;
                    }
                    normals[nOff] = n0;     normals[nOff + 1] = n1; normals[nOff + 2] = n2;
                    normals[nOff + 3] = n3; normals[nOff + 4] = n4; normals[nOff + 5] = n5;
                    normals[nOff + 6] = n6; normals[nOff + 7] = n7; normals[nOff + 8] = n8;
                }
            }

            boolean ownVisible = boneParams[p + 9] == 0.0f;
            visible[bone] = ownVisible;
            descendants[bone] = boneParams[p + 10] == 0.0f;

            if (stateBuffer != null && ownVisible && boneParams[p + 11] == 1.0f) {
                int sOff = bone * 4;
                if (sOff + 2 < stateBuffer.length) {
                    int pv = bone * 3;
                    double px = pivots[pv];
                    double py = pivots[pv + 1];
                    double pz = pivots[pv + 2];
                    float lx = (float) (w03 + (double) w00 * px + (double) w01 * py + (double) w02 * pz);
                    float ly = (float) (w13 + (double) w10 * px + (double) w11 * py + (double) w12 * pz);
                    float lz = (float) (w23 + (double) w20 * px + (double) w21 * py + (double) w22 * pz);
                    if (!Float.isFinite(lx) || !Float.isFinite(ly) || !Float.isFinite(lz)) {
                        return false;
                    }
                    stateBuffer[sOff] = -lx * 16.0f;
                    stateBuffer[sOff + 1] = ly * 16.0f;
                    stateBuffer[sOff + 2] = lz * 16.0f;
                }
            }
        }
        return true;
    }

    private static void writeHiddenBoneFallback(ByteBuffer out, boolean floatTail) {
        writeIdentity4(out);
        writeIdentity4(out);
        writeTail(out, 0, true, floatTail);
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

    private static void writeIdentity4(ByteBuffer out) {
        out.putFloat(1.0f); out.putFloat(0.0f); out.putFloat(0.0f); out.putFloat(0.0f);
        out.putFloat(0.0f); out.putFloat(1.0f); out.putFloat(0.0f); out.putFloat(0.0f);
        out.putFloat(0.0f); out.putFloat(0.0f); out.putFloat(1.0f); out.putFloat(0.0f);
        out.putFloat(0.0f); out.putFloat(0.0f); out.putFloat(0.0f); out.putFloat(1.0f);
    }

    private static final class Scratch {
        final EvaluatedBones view = new EvaluatedBones();
        float[] transforms = new float[0];
        float[] normals = new float[0];
        boolean[] visible = new boolean[0];
        boolean[] descendants = new boolean[0];
        double[] uniformScale = new double[0];

        void ensureCapacity(int boneCount, boolean needNormals) {
            if (visible.length < boneCount) {
                transforms = new float[boneCount * 16];
                visible = new boolean[boneCount];
                descendants = new boolean[boneCount];
                uniformScale = new double[boneCount];
            }
            if (needNormals && normals.length < boneCount * 9) {
                normals = new float[boneCount * 9];
            }
        }
    }
}
