package com.micaftic.morpher.core.gpu;

import com.micaftic.morpher.client.render.BuiltModel;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;

public final class Blaze3DBoneMatrices {
    private Blaze3DBoneMatrices() {
    }

    /** Computes evaluated bone matrices in 144B/bone float-tail layout via {@code spm-core}. */
    public static boolean write(
            BuiltModel model,
            Matrix4f rootPose,
            Matrix3f rootNormal,
            float[] boneParams,
            float[] stateBuffer,
            int packedLight,
            ByteBuffer out
    ) {
        return SpmCoreBoneMatrixWriter.writeEvaluated(
                model, rootPose, rootNormal, boneParams, stateBuffer, packedLight, out, true
        );
    }
}
