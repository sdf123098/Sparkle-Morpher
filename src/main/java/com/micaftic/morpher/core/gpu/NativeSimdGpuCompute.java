package com.micaftic.morpher.core.gpu;

import java.nio.ByteBuffer;

/**
 * Guards the Native SIMD -> GPU bone-buffer hand-off.
 *
 * <p>The JNI method is intentionally kept ABI-compatible with shipped native
 * libraries and therefore returns {@code void}. The native writer marks every
 * bone record's hidden flag, so Java can detect an early native return before
 * uploading an incomplete buffer to the GPU.</p>
 */
public final class NativeSimdGpuCompute {

    public static final int BONE_STRIDE_BYTES = 144;
    public static final int HIDDEN_FLAG_OFFSET = 136;
    public static final int VULKAN_HIDDEN_FLAG_OFFSET = HIDDEN_FLAG_OFFSET;

    private static final int UNWRITTEN_SENTINEL = 0x7FC00001;

    private NativeSimdGpuCompute() {
    }

    static void markUnwritten(ByteBuffer buffer, int boneCount) {
        if (buffer == null || boneCount < 0) {
            return;
        }
        for (int bone = 0; bone < boneCount; bone++) {
            int offset = bone * BONE_STRIDE_BYTES + HIDDEN_FLAG_OFFSET;
            if (offset + Integer.BYTES > buffer.capacity()) {
                return;
            }
            buffer.putInt(offset, UNWRITTEN_SENTINEL);
        }
        buffer.position(0);
    }

    static boolean hasCompleteWrite(ByteBuffer buffer, int boneCount) {
        if (buffer == null || boneCount <= 0
                || buffer.capacity() < boneCount * BONE_STRIDE_BYTES) {
            return false;
        }
        for (int bone = 0; bone < boneCount; bone++) {
            int offset = bone * BONE_STRIDE_BYTES + HIDDEN_FLAG_OFFSET;
            int hidden = buffer.getInt(offset);
            if (hidden == UNWRITTEN_SENTINEL || (hidden != 0 && hidden != 1)) {
                return false;
            }
        }
        return true;
    }

    static void markVulkanUnwritten(ByteBuffer buffer, int boneCount) {
        markUnwritten(buffer, boneCount);
    }

    static boolean hasCompleteVulkanWrite(ByteBuffer buffer, int boneCount) {
        if (buffer == null || boneCount <= 0
                || buffer.capacity() < boneCount * BONE_STRIDE_BYTES) {
            return false;
        }
        for (int bone = 0; bone < boneCount; bone++) {
            int offset = bone * BONE_STRIDE_BYTES + VULKAN_HIDDEN_FLAG_OFFSET;
            float hidden = buffer.getFloat(offset);
            if (Float.isNaN(hidden) || (hidden != 0.0f && hidden != 1.0f)) {
                return false;
            }
        }
        return true;
    }
}
