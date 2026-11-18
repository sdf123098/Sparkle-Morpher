package com.micaftic.morpher.core.gpu;

import com.micaftic.morpher.client.render.BuiltModel;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * 骨骼矩阵计算（从 GpuRenderPath 抽离，现代 HUD 阶段 2 复用）。
 *
 * <p>输入：模型骨骼 + 世界帧动画评估结果（{@code boneParams}/{@code stateBuffer}，来自
 * {@code PlayerPoseSnapshot}）+ rootPose/rootNormal（模型空间 → 观察空间变换）。
 * 输出：每骨骼 144 bytes 的 GPU 布局（4x4 全局矩阵 + 4x4 法线 + light + 保留），
 * 与 {@link GpuRenderPath} 世界渲染完全一致 —— HUD 与世界共用同一套骨骼算法。
 *
 * <p>非线程安全（静态 scratch）；必须在渲染线程调用。
 */
public final class BoneMatrixComputer {

    public static final int FULL_BRIGHT_LIGHT = 0xF000F0;

    private static final Matrix4f pivotAbsScratchMat = new Matrix4f();
    private static int[] pivotAbsPathScratch = new int[64];

    private BoneMatrixComputer() {
    }

    /**
     * 计算全部骨骼的全局矩阵并写入 {@code out}（每骨骼 144 bytes）。返回 false 表示 boneParams 无效。
     */
    public static boolean compute(
            BuiltModel model,
            Matrix4f rootPose,
            Matrix3f rootNormal,
            float[] boneParams,
            float[] stateBuffer,
            int packedLight,
            ByteBuffer out
    ) {
        return SpmCoreBoneMatrixWriter.writeEvaluated(
                model, rootPose, rootNormal, boneParams, stateBuffer, packedLight, out, false
        );
    }

    /** 供 pivot 绝对路径回写等扩展使用（与 GpuRenderPath 原语义一致，当前保留未用）。 */
    static Matrix4f pivotAbsScratch() {
        return pivotAbsScratchMat;
    }

    static int[] pivotAbsPathScratch() {
        return pivotAbsPathScratch;
    }
}
