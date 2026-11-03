package com.elfmcys.yesstevemodel.geckolib3.geo.render.built;

import java.nio.ByteBuffer;

final class ModelAccelerationBridge {
    private ModelAccelerationBridge() {
    }

    static native int nGetAbiVersion();

    static native long nInitModelCache(ByteBuffer buffer);

    static native void nDestroyModelCache(long handle);

    static native void nComputeModelVertices(
            long handle, Object vertexConsumer,
            float[] matrixTransfer, float[] animTransfer,
            int renderPartMask, int packedLight, int packedOverlay,
            float r, float g, float b, float a);

    static native long nBuildGpuMesh(ByteBuffer buffer, int[] outMeta);

    static native ByteBuffer nGetGpuMeshVertexBuffer(long pointer);

    static native ByteBuffer nGetGpuMeshIndexBuffer(long pointer);

    static native void nReleaseGpuMeshScratch(long pointer);

    static native void nFreeGpuMesh(long pointer);

    static native void nComputeBoneMatrices(long pointer, float[] rootPose, float[] rootNormal, float[] anim, int packedLight, ByteBuffer outBoneBuffer);

    static native void nComputeBoneMatricesLocal(long handle, float[] animArray, int packedLight, ByteBuffer outBoneBuffer);
}
