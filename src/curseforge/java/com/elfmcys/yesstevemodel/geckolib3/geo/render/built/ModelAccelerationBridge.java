package com.elfmcys.yesstevemodel.geckolib3.geo.render.built;

import java.nio.ByteBuffer;

final class ModelAccelerationBridge {
    private ModelAccelerationBridge() {
    }

    static int nGetAbiVersion() {
        return 0;
    }

    static long nInitModelCache(ByteBuffer buffer) {
        return 0L;
    }

    static void nDestroyModelCache(long handle) {
    }

    static void nComputeModelVertices(long handle, Object vertexConsumer, float[] matrixTransfer, float[] animTransfer, int renderPartMask, int packedLight, int packedOverlay, float r, float g, float b, float a) {
    }

    static long nBuildGpuMesh(ByteBuffer buffer, int[] outMeta) {
        return 0L;
    }

    static ByteBuffer nGetGpuMeshVertexBuffer(long pointer) {
        return null;
    }

    static ByteBuffer nGetGpuMeshIndexBuffer(long pointer) {
        return null;
    }

    static void nReleaseGpuMeshScratch(long pointer) {
    }

    static void nFreeGpuMesh(long pointer) {
    }

    static void nComputeBoneMatrices(long pointer, float[] rootPose, float[] rootNormal, float[] anim, int packedLight, ByteBuffer outBoneBuffer) {
    }

    static void nComputeBoneMatricesLocal(long handle, float[] animArray, int packedLight, ByteBuffer outBoneBuffer) {
    }
}
