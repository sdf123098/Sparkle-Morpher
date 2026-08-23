package com.micaftic.morpher.core.gpu;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeSimdGpuComputeTest {

    @Test
    void rejectsBufferWhenNativeWritesNothing() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(NativeSimdGpuCompute.BONE_STRIDE_BYTES)
                .order(ByteOrder.nativeOrder());

        NativeSimdGpuCompute.markUnwritten(buffer, 1);

        assertFalse(NativeSimdGpuCompute.hasCompleteWrite(buffer, 1));
    }

    @Test
    void acceptsBufferWhenNativeWritesEveryBoneRecord() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(NativeSimdGpuCompute.BONE_STRIDE_BYTES * 2)
                .order(ByteOrder.nativeOrder());

        NativeSimdGpuCompute.markUnwritten(buffer, 2);
        buffer.putInt(NativeSimdGpuCompute.HIDDEN_FLAG_OFFSET, 0);
        buffer.putInt(NativeSimdGpuCompute.BONE_STRIDE_BYTES + NativeSimdGpuCompute.HIDDEN_FLAG_OFFSET, 1);

        assertTrue(NativeSimdGpuCompute.hasCompleteWrite(buffer, 2));
    }

    @Test
    void rejectsBufferThatCannotHoldAllBoneRecords() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(NativeSimdGpuCompute.BONE_STRIDE_BYTES)
                .order(ByteOrder.nativeOrder());

        assertFalse(NativeSimdGpuCompute.hasCompleteWrite(buffer, 2));
    }
}
